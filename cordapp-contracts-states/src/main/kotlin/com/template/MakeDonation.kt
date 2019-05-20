package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.*
import net.corda.finance.DOLLARS
import net.corda.finance.GBP
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.TwoPartyDealFlow
import org.hibernate.Session
import java.security.PublicKey
import java.time.Instant
import java.util.Date
import java.util.*
import javax.validation.constraints.Null

/** Donation flow: After donor received a Campaign from fundraiser,
  * donor can make a donation to a specific campaign */

object MakeDonation{
     /** Create a new donation state for updating the existing campaign state*/
     @StartableByRPC
     @InitiatingFlow
     class Initiator(
             private val amount: Amount<Currency>,
             private val campaignReference: UniqueIdentifier,
             private val paymentMethod: String
     ) : FlowLogic<SignedTransaction>() {

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
         private fun selfCashIssue(amount: Amount<Currency>): FlowLogic<AbstractCashFlow.Result> {
             val notary = serviceHub.networkMapCache.notaryIdentities.first()
             val issueRef = OpaqueBytes.of(0)
             val issueRequest = CashIssueFlow.IssueRequest(amount, issueRef, notary)
             val flow = CashIssueFlow(issueRequest)
             logger.info("inside Cash issue flow")
             return flow
         }
         @Suspendable
         override fun call(): SignedTransaction {
             //Pick notary
             val notary = serviceHub.networkMapCache.notaryIdentities.first()
            subFlow(selfCashIssue(amount))
             val queryCash = serviceHub.getCashBalance(USD).toString()
             logger.info("Make donation CashBalance: $queryCash")
             val cash = serviceHub.vaultService.queryBy<Cash.State>().states
             logger.info("Make donation CashQuery: $cash")


             //Query Campaign state from donor's vault
             logger.info("campaignId $campaignReference")

             val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(campaignReference))
             logger.info("queryCriteria: $queryCriteria")
             val campaignInputStateAndRef = serviceHub.vaultService.queryBy<Campaign>(queryCriteria).states.get(0)
             logger.info("campaignInputStateAndRef ${campaignInputStateAndRef.state}")

             val campaignState = campaignInputStateAndRef.state.data
             logger.info("MakeDonationF1: ${campaignState.fundraiser}")
             logger.info("MakeDonationD1: ${campaignState.recipient}")
             logger.info("makedoantion: $campaignState")

             // Generate anonymous key in order to other donors don't know who we are
             val myKey = serviceHub.keyManagementService.freshKeyAndCert(
                     ourIdentityAndCert,
                     revocationEnabled = false
             ).party.anonymise()

             // Assemble the transaction component
             val acceptDonationCommand = Command(CampaignContract.Commands.AcceptDonation(), listOf(campaignState.fundraiser.owningKey))
             val createDonationCommand = Command(DonationContract.Create(), listOf(myKey.owningKey))

             //Output states
             val timeStamp = Instant.now()
             logger.info("timeStamp: $timeStamp")
             val donationOutputState = Donation(campaignReference,campaignState.fundraiser,myKey,campaignState.bank,amount,timeStamp,paymentMethod)
             val donationOutputStateAndContract = StateAndContract(donationOutputState,DonationContract.ID)
             val newRaised = campaignState.raised + amount
             val newRemainingAmount = newRaised - campaignState.transferAmount
             logger.info("newRemainingAmount: $newRemainingAmount")
             val campaignOutputState = campaignState.copy(raised = newRaised, remainingAmount = newRemainingAmount)

             val campaignOutputStateAndContract = StateAndContract(campaignOutputState,CampaignContract.ID)

            //Build transaction
             val utx = TransactionBuilder(notary = notary).withItems(
                    donationOutputStateAndContract, //Output state
                     campaignOutputStateAndContract,//Output state
                     campaignInputStateAndRef,      //Input
                     acceptDonationCommand,         //Command
                     createDonationCommand          //Command

             )

             utx.setTimeWindow(Instant.now(),30.seconds)  // TODO
             //Sign, sync, finalise, and commit transaction
             val ptx = serviceHub.signInitialTransaction(builder = utx,signingPubKeys = listOf(myKey.owningKey))
             val session = initiateFlow(campaignState.fundraiser)
             subFlow(IdentitySyncFlow.Send(otherSide = session, tx = ptx.tx))
             logger.info("ptx: $ptx")
             logger.info("session: $session")
             //Collect signature
             val stx = subFlow(CollectSignaturesFlow(ptx, setOf(session), setOf(myKey.owningKey)))
             val ftx = subFlow(FinalityFlow(stx))
             logger.info("stx: $stx")
             logger.info("ftx: $ftx")

             return ftx

         }
     }
    /**
     * The responder run by fundraiser, to check the proposed transaction
     * and sign the transaction
     * */
    @InitiatedBy(Initiator::class)
    class Responder(val othersession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("Responder1")
            subFlow(IdentitySyncFlow.Receive(otherSideSession = othersession))
            logger.info("othersession: $othersession")
            val flow: SignTransactionFlow = object :SignTransactionFlow(othersession){
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                }
            }
            val stx: SignedTransaction = subFlow(flow)
            logger.info("Responder3")
            //wait for transaction has been committed
            val ftx = waitForLedgerCommit(stx.id)
            logger.info("Responder4")
            othersession.send(Unit)
            logger.info("othersession2: $othersession")
            logger.info("Responder5")

        }
    }
 }