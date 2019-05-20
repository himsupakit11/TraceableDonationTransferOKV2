package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.DOLLARS
import net.corda.finance.flows.TwoPartyDealFlow

object AutoOfferFlow {
    @InitiatingFlow
    @StartableByRPC
    /** This flow run by fundraiser for creating a new campagin */
    class StartCampaign(private val newCampaign: Campaign) : FlowLogic<SignedTransaction>() {
        companion object {
            object RECEIVED : ProgressTracker.Step("Received API call")
            object DEALING : ProgressTracker.Step("Starting the deal flow") {
                override fun childProgressTracker(): ProgressTracker = TwoPartyDealFlow.Primary.tracker()
            }

            fun tracker() = ProgressTracker(RECEIVED, DEALING)
        }

        override val progressTracker = tracker()

        init {
            progressTracker.currentStep = RECEIVED
        }

        @Suspendable
        override fun call(): SignedTransaction {
            //Pick notary
            logger.info("Start Campaign Successfully")
            val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()
            //Assemble the campaign components
            val startCommand = Command(CampaignContract.Commands.Start(), listOf(ourIdentity.owningKey))
            val outputState = StateAndContract(newCampaign, CampaignContract.ID)
            //Build, sign and record the campaign
            val utx = TransactionBuilder(notary = notary).withItems(
                    outputState,
                    startCommand
            )
            val stx = serviceHub.signInitialTransaction(utx)
            val ftx = subFlow(FinalityFlow(stx))
            logger.info("Broadcast Campaign successfully")
            return ftx
        }
    }

}