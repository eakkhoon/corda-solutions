package net.corda.businessnetworks.ticketing.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
@InitiatingFlow
class RequestTicketFlow() : FlowLogic<SignedTransaction>() {

    companion object {
        object CREATING_TICKET_IN_PENDING : ProgressTracker.Step("Creating ticket in pending status")
        object SIGNED_BY_BNO : ProgressTracker.Step("Signed by BNO")
        object FINALIZING : ProgressTracker.Step("Finalizing")

        fun tracker() = ProgressTracker(
                CREATING_TICKET_IN_PENDING,
                SIGNED_BY_BNO,
                FINALIZING
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SignedTransaction {
        /*
        progressTracker.currentStep = SENDING_MEMBERSHIP_DATA_TO_BNO
        val configuration = serviceHub.cordaService(MemberConfigurationService::class.java)
        val bno = configuration.bnoParty()

        val bnoSession = initiateFlow(bno)
        bnoSession.send(OnBoardingRequest(membershipMetadata))

        val signResponder = object : SignTransactionFlow(bnoSession) {
            override fun checkTransaction(stx : SignedTransaction) {
                val command = stx.tx.commands.single()
                if (command.value !is Membership.Commands.Request) {
                    throw FlowException("Only Request command is allowed")
                }

                val output = stx.tx.outputs.single()
                if (output.contract != Membership.CONTRACT_NAME) {
                    throw FlowException("Output state has to be verified by ${Membership.CONTRACT_NAME}")
                }
                val membershipState = output.data as Membership.State
                if (bno != membershipState.bno) {
                    throw IllegalArgumentException("Wrong BNO identity")
                }
                if (ourIdentity != membershipState.member) {
                    throw IllegalArgumentException("We have to be the member")
                }

                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }
        progressTracker.currentStep = ACCEPTING_INCOMING_PENDING_MEMBERSHIP
        return subFlow(signResponder)
        */
        throw RuntimeException("not implemented yet")
    }
}
