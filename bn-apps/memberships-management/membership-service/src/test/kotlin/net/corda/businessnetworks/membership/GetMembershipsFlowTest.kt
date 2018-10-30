package net.corda.businessnetworks.membership

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.GetMembershipListFlowResponder
import net.corda.businessnetworks.membership.member.service.MembershipsCacheHolder
import net.corda.businessnetworks.membership.states.MembershipContract
import net.corda.businessnetworks.membership.states.MembershipState
import net.corda.businessnetworks.membership.states.MembershipStatus
import net.corda.businessnetworks.membership.states.SimpleMembershipMetadata
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class GetMembershipsFlowTest : AbstractFlowTest(5) {
    override fun registerFlows() {
        bnoNode.registerInitiatedFlow(GetMembershipListFlowResponder::class.java)
    }

    @Test
    fun `all nodes should be getting the same list of memberships`() {
        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        val allParties = participantsNodes.map { identity(it) }.toSet()
        participantsNodes.forEach {
            val memberships = runGetMembershipsListFlow(it, true)
            assert(memberships.map { it.value.state.data.member }.toSet() == allParties)
            val party = identity(it)
            assert(memberships[party] == getMembership(it, party))
        }
    }


    @Test
    fun `all members should be included into the list`() {
        val suspendedNode = participantsNodes[0]
        val pendingNode = participantsNodes[1]
        val okNode = participantsNodes[2]

        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            if (it != pendingNode)
                runActivateMembershipFlow(bnoNode, identity(it))
        }
        runSuspendMembershipFlow(bnoNode, identity(suspendedNode))

        val membershipsSnapshot = runGetMembershipsListFlow(okNode, true)

        val bnoMemberships = getBNOMemberships()
        assertEquals(bnoMemberships.toSet(), membershipsSnapshot.values.toSet())
    }

    @Test
    fun `only active members should be able to use this flow`() {
        val suspendedNode = participantsNodes[0]
        val pendingNode = participantsNodes[1]
        val notMember = participantsNodes[3]

        runRequestMembershipFlow(suspendedNode)
        runRequestMembershipFlow(pendingNode)
        runSuspendMembershipFlow(bnoNode, identity(suspendedNode))

        try {
            runGetMembershipsListFlow(notMember, true)
            fail()
        } catch (e : NotAMemberException) {
            assert("Counterparty ${identity(notMember)} is not a member of this business network" == e.message)
        }
        try {
            runGetMembershipsListFlow(pendingNode, true)
            fail()
        } catch (e : MembershipNotActiveException) {
            assert("Counterparty's ${identity(pendingNode)} membership in this business network is not active" == e.message)
        }
        try {
            runGetMembershipsListFlow(suspendedNode, true)
            fail()
        } catch (e : MembershipNotActiveException) {
            assert("Counterparty's ${identity(suspendedNode)} membership in this business network is not active" == e.message)
        }
    }

    @Test
    fun `nodes that are not in the Network Map should be filtered out from the list`() {
        // requesting memberships
        participantsNodes.forEach {
            runRequestMembershipFlow(it)
            runActivateMembershipFlow(bnoNode, identity(it))
        }

        val participant = participantsNodes.first()
        runGetMembershipsListFlow(participant, true)

        // adding not existing party to the cache
        val notExistingParty = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB")).party
        val future = participant.startFlow(AddNotExistingPartyToMembershipsCache(bnoParty, MembershipState(notExistingParty, bnoParty, SimpleMembershipMetadata("DEFAULT"), status = MembershipStatus.ACTIVE)))
        mockNetwork.runNetwork()
        future.getOrThrow()

        // not existing parties shouldn't appear on the result list
        val membersWithoutNotExisting = runGetMembershipsListFlow(participant, false, true)
        assertFalse(membersWithoutNotExisting.map { it.value.state.data.member }.contains(notExistingParty))

        // not existing parties should appear on the result list is filterOutNotExisting flag has been explicitly set to false
        val membersWithNotExisting = runGetMembershipsListFlow(participant, false, false)
        assertTrue (membersWithNotExisting.map { it.value.state.data.member }.contains(notExistingParty))
    }
}

class AddNotExistingPartyToMembershipsCache(val bno : Party, val membership : MembershipState<SimpleMembershipMetadata>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val cacheHolder = serviceHub.cordaService(MembershipsCacheHolder::class.java)
        val stateAndRef = StateAndRef(TransactionState(membership, MembershipContract.CONTRACT_NAME, serviceHub.networkMapCache.notaryIdentities.single()), StateRef(SecureHash.zeroHash, 0))
        cacheHolder.cache.updateMembership(stateAndRef)
    }
}