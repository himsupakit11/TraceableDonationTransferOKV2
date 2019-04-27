package com.template

import com.template.contractAndstate.EndCampaign
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.DOLLARS
import net.corda.finance.GBP
import net.corda.finance.USD
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.h2.Driver
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

class EndCampaignTest{
    lateinit var network: MockNetwork
    lateinit var Bank: StartedMockNode
    lateinit var Donor: StartedMockNode
    lateinit var Fundraiser: StartedMockNode
    lateinit var Recipient: StartedMockNode
    @Before
    fun setup(){
        network = MockNetwork(cordappPackages =  listOf("com.template","net.corda.finance"), threadPerNode =  true)
        Bank = network.createPartyNode(CordaX500Name("Bank","Bangkok","TH"))
        Donor = network.createPartyNode(CordaX500Name("Alice","BKK","TH"))
        Recipient = network.createPartyNode(CordaX500Name("Hospital","PCKK", "TH"))
        Fundraiser = network.createPartyNode(CordaX500Name("PToon","BKK","TH"))

        listOf(Fundraiser, Bank, Donor, Recipient).forEach { it.registerInitiatedFlow(AutoOfferFlow.RecordTransactionAsObserver::class.java) }
        listOf(Fundraiser, Bank, Donor, Recipient).forEach { it.registerInitiatedFlow(MakeDonation.Responder::class.java)}
        listOf(Fundraiser,Donor, Recipient).forEach { it.registerInitiatedFlow(EndCampaign.Responder::class.java) }

    }
    @After
    fun tearDown(){
        network.stopNodes()
    }
    companion object {
        val logger: Logger = loggerFor<MakeDonationTest>()
    }

    private fun selfCashIssue(node:StartedMockNode,amount: Amount<Currency>): SignedTransaction {
        val notary = network.defaultNotaryIdentity
        val issueRef = OpaqueBytes.of(0)
        val issueRequest = CashIssueFlow.IssueRequest(amount, issueRef, notary)
        val flow = CashIssueFlow(issueRequest)
        println("cashFlow: $flow")
        return node.startFlow(flow).getOrThrow().stx
    }
    private fun calculatedDeadlineInSeconds(interval: Long) = Instant.now().plusSeconds(interval)
    private val fiveSecondsFromNow: Instant get() = calculatedDeadlineInSeconds(5L)
    private val OneSecondsFromNow: Instant get() = calculatedDeadlineInSeconds(5L)
    //    private val kaokonlakaoCampaign
//        get() = Campaign(
//                name = "Kaokonlakao Campaign",
//                target = 1000.DOLLARS,
//                fundraiser = Fundraiser.services.myInfo.legalIdentities.first(),
//                recipient = Recipient.services.myInfo.legalIdentities.first(),
//                deadline = fiveSecondsFromNow,
//                category = "Charity"
//        )
       @Test
    fun`Make a donation and broadcast the updated campaign state to all parties successfully`(){

        val kaokonlakaoCampaign = Campaign(
                name = "Kaokonlakao Campaign",
                target = 1000.DOLLARS,
                remainingAmount = 0.DOLLARS,
                transferAmount = 0.DOLLARS,
                fundraiser = Fundraiser.services.myInfo.legalIdentities.first(),
                recipient = Recipient.services.myInfo.legalIdentities.first(),
                donor = Donor.services.myInfo.legalIdentities.first(),
                deadline = OneSecondsFromNow,
                recipientName = "Hospital",
                category = "Charity",
                description = "abcdef",
                objective = "ghjkl")


//        selfCashIssue(Fundraiser,0.DOLLARS)
//        selfCashIssue(Recipient,0.DOLLARS)
//        selfCashIssue(Bank,0.DOLLARS)


//        val donorIssueCash2 = selfCashIssue(Donor,2000.DOLLARS)
//        val donorIssueCash3 = selfCashIssue(Donor,3000.DOLLARS)
//        // Start a new campaign
        val flow = AutoOfferFlow.StartCampaign(kaokonlakaoCampaign)
        val createCampaignTransaction = Fundraiser.startFlow(flow).getOrThrow()
        network.waitQuiescent()


        // Get the campaign state from the transaction
        val campaignState = createCampaignTransaction.tx.outputs.single().data as Campaign
        logger.info("CampaignState: ${campaignState}")
        val campaignId = campaignState.linearId
        val a = Recipient.services.vaultService.queryBy<Campaign>().states
        val b = Donor.services.vaultService.queryBy<Campaign>().states
        val c = Bank.services.vaultService.queryBy<Campaign>().states
        val d = Fundraiser.services.vaultService.queryBy<Campaign>().states
        logger.info("===========================================")
        logger.info("Recipient Vault : $a")
        logger.info("Donor Vault     : $b")
        logger.info("Bank Vault      : $c")
        logger.info("Fundraiser Vault: $d")
        logger.info("===========================================")

//        val notary = network.defaultNotaryIdentity
//        val issueRef = OpaqueBytes.of(0)
//        val issueRequest = CashIssueFlow.IssueRequest(1200.DOLLARS, issueRef, notary)
//        val flow1 = CashIssueFlow(issueRequest)
//        val temp = Donor.startFlow(flow1).getOrThrow()




        //Issue token
        val donorIssueCash = selfCashIssue(Donor,3000.DOLLARS)
        //val donorIssueCash2 = selfCashIssue(Donor,3000.DOLLARS)
        /**Donor make a donation to fundraiser*/
        val makeDonationFlow= MakeDonation.Initiator(3000.DOLLARS, campaignId,"Credit Card")
        logger.info("MakeDonationFlow: $makeDonationFlow")
        val acceptDonationTransaction = Donor.startFlow(makeDonationFlow).getOrThrow()
        logger.info("acceptDonationTransaction: $acceptDonationTransaction")

        logger.info("New campaign started")
        logger.info(createCampaignTransaction.toString()) //Print tx id
        logger.info(createCampaignTransaction.tx.toString())

        logger.info("Donor make a donation to fundraiser $1200")
        logger.info(acceptDonationTransaction.toString())
        logger.info(acceptDonationTransaction.tx.toString())

        /**Donor make a donation to fundraiser*/
//        selfCashIssue(Donor,2000.DOLLARS)
//        val makeDonationFlow2= MakeDonation.Initiator(2000.DOLLARS, campaignId)
//        logger.info("MakeDonationFlow: $makeDonationFlow")
//        val acceptDonationTransaction2 = Donor.startFlow(makeDonationFlow2).getOrThrow()
//        logger.info("acceptDonationTransaction: $acceptDonationTransaction")
//
//        logger.info("Donor make another a donation to fundraiser $1200")
//        logger.info(acceptDonationTransaction2.toString())
//        logger.info(acceptDonationTransaction2.tx.toString())



//        // Donor make a donation to fundraiser
//        val makeDonationFlow2= MakeDonation.Initiator(2000.DOLLARS, campaignId, broadcastToObservers = true)
//        logger.info("MakeDonationFlow: $makeDonationFlow2")
//        val acceptDonationTransaction2 = Donor.startFlow(makeDonationFlow2).getOrThrow()
//        val campaignStateAfterSecondPledge = acceptDonationTransaction2.tx.outputsOfType<Campaign>().single()
//        val campaignStateRefAfterSecondPledge = acceptDonationTransaction2.tx.outRefsOfType<Campaign>().single().ref
//        val secondDonation = acceptDonationTransaction2.tx.outputsOfType<Donation>().single()
//        logger.info("acceptDonationTransaction: $acceptDonationTransaction2")
//        logger.info("secondDonation: $secondDonation")
//
//
//        logger.info("Donor make a donation to fundraiser $2000")
//        logger.info(acceptDonationTransaction2.toString())
//        logger.info(acceptDonationTransaction2.tx.toString())

//// Donor make a donation to fundraiser
//        val makeDonationFlow3= MakeDonation.Initiator(3000.DOLLARS, campaignId, broadcastToObservers = true)
//        logger.info("MakeDonationFlow: $makeDonationFlow3")
//        val acceptDonationTransaction3 = Donor.startFlow(makeDonationFlow3).getOrThrow()
//        val campaignStateAfterThirdledge = acceptDonationTransaction3.tx.outputsOfType<Campaign>().single()
//        val campaignStateRefAfterThirdPledge = acceptDonationTransaction3.tx.outRefsOfType<Campaign>().single().ref
//        val thirdDonation = acceptDonationTransaction3.tx.outputsOfType<Donation>().single()
//        logger.info("thirdDonation: $thirdDonation")
//
//        logger.info("Donor make a donation to fundraiser $3000")
//        logger.info(acceptDonationTransaction3.toString())
//        logger.info(acceptDonationTransaction3.tx.toString())

        /**Make receipt flow*/
        val makeReceiptFlow = MakeReceipt.Initiator(campaignId, 1000.DOLLARS)
        val startReceiptFlow = Fundraiser.startFlow(makeReceiptFlow).getOrThrow()
        logger.info("Start ReceiptFlow Successfully  ")
        logger.info(makeReceiptFlow.toString())
        logger.info(startReceiptFlow.tx.toString())
        /*********************/
//        val x = Recipient.services.vaultService.queryBy<Receipt>().states
//        val j = Donor.services.vaultService.queryBy<Receipt>().states
//        val y = Bank.services.vaultService.queryBy<Receipt>().states
//        val z = Fundraiser.services.vaultService.queryBy<Receipt>().states
//        logger.info("===========================================")
//        logger.info("Recipient Receipt Vault : $x")
//        logger.info("Donor Receipt Vault     : $j")
//        logger.info("Bank Receipt Vault      : $y")
//        logger.info("Fundraiser Receipt Vault: $z")
//        logger.info("===========================================")

        // Get the campaign state from the transaction
        val campaignStateAfterDonaion = acceptDonationTransaction.tx.outRefsOfType<Campaign>().single().ref
        val campaignAfterDonaiton = acceptDonationTransaction.tx.outputsOfType<Campaign>().single()
        val newDonationStateRef = acceptDonationTransaction.tx.outRefsOfType<Donation>().single().ref
        val newDonation= acceptDonationTransaction.tx.outputsOfType<Donation>().single()

        /**End campaign flow*/
//        val endCampaignFlow = EndCampaign.Initiator(campaignStateAfterDonaion)
//        val startEndCampaignFlow = Fundraiser.startFlow(endCampaignFlow).getOrThrow()
//        logger.info("###################### Bank #########################")
//        logger.info(Bank.services.getCashBalance(USD).toString())
//        logger.info("###################### Donor #########################")
//        logger.info(Donor.services.getCashBalance(USD).toString())
//        logger.info("###################### Recipient #########################")
//        logger.info(Recipient.services.getCashBalance(USD).toString())
//        logger.info("###################### Fundraiser #########################")
//        logger.info(Fundraiser.services.getCashBalance(USD).toString())
//

//        Fundraiser.transaction {
//            val (_, observable) = Fundraiser.services.validatedTransactions.track()
//            observable.subscribe { tx ->
//                // Don't log dependency transactions.
//                val myKeys = Fundraiser.services.keyManagementService.filterMyKeys(tx.tx.requiredSigningKeys).toList()
//                if (myKeys.isNotEmpty()) {
//                    logger.info("Print ###########")
//                    logger.info(tx.tx.toString())
//                }
//            }
//        }
        network.waitQuiescent()
    }


















//    @Test
//    fun`Make a donation to different campaign`(){
//
//        val kaokonlakaoCampaign = Campaign(
//                name = "Kaokonlakao Campaign",
//                target = 1000.DOLLARS,
//                fundraiser = Fundraiser.services.myInfo.legalIdentities.first(),
//                recipient = Recipient.services.myInfo.legalIdentities.first(),
//                donor = Donor.services.myInfo.legalIdentities.first(),
//                deadline = OneSecondsFromNow,
//                category = "Charity")
//
//        val testCampagin = Campaign(
//                name = "Campaign",
//                target = 99999.DOLLARS,
//                fundraiser = Fundraiser.services.myInfo.legalIdentities.first(),
//                recipient = Recipient.services.myInfo.legalIdentities.first(),
//                donor = Donor.services.myInfo.legalIdentities.first(),
//                deadline = OneSecondsFromNow,
//                category = "Education")
//
//        //Issue token
//        val donorIssueCash = selfCashIssue(Donor,1000.DOLLARS)
//        val donorIssueCash2 = selfCashIssue(Donor,2000.DOLLARS)
//        val donorIssueCash3 = selfCashIssue(Donor,3000.DOLLARS)
//        // Start a new campaign
//        val flow = AutoOfferFlow.StartCampaign(kaokonlakaoCampaign)
//        val createCampaignTransaction = Fundraiser.startFlow(flow).getOrThrow()
//        network.waitQuiescent()
//
//
//        // Get the campaign state from the transaction
//        val campaignState = createCampaignTransaction.tx.outputs.single().data as Campaign
//        logger.info("CampaignState: ${campaignState}")
//        val campaignId = campaignState.linearId
//        val a = Recipient.services.vaultService.queryBy<Campaign>().states
//        val b = Donor.services.vaultService.queryBy<Campaign>().states
//        val c = Bank.services.vaultService.queryBy<Campaign>().states
//        val d = Fundraiser.services.vaultService.queryBy<Campaign>().states
//        logger.info("===========================================")
//        logger.info("Recipient Vault : $a")
//        logger.info("Donor Vault     : $b")
//        logger.info("Bank Vault      : $c")
//        logger.info("Fundraiser Vault: $d")
//        logger.info("===========================================")
//
//
//        //Start the second campaign
//        val flow2 = AutoOfferFlow.StartCampaign(testCampagin)
//        val createCampaignTransaction2 = Fundraiser.startFlow(flow2).getOrThrow()
//        val campaignState2 = createCampaignTransaction2.tx.outputs.single().data as Campaign
//        logger.info("CampaignState: ${campaignState2}")
//        val campaignId2 = campaignState2.linearId
//        network.waitQuiescent()
//
//
//
//
//
////        val notary = network.defaultNotaryIdentity
////        val issueRef = OpaqueBytes.of(0)
////        val issueRequest = CashIssueFlow.IssueRequest(1200.DOLLARS, issueRef, notary)
////        val flow1 = CashIssueFlow(issueRequest)
////        val temp = Donor.startFlow(flow1).getOrThrow()
//
//
//
//
//
//        // Donor make a donation to fundraiser
//        val makeDonationFlow= MakeDonation.Initiator(1000.DOLLARS, campaignId, broadcastToObservers = true)
//        logger.info("MakeDonationFlow: $makeDonationFlow")
//        val acceptDonationTransaction = Donor.startFlow(makeDonationFlow).getOrThrow()
//        logger.info("acceptDonationTransaction: $acceptDonationTransaction")
//
//        logger.info("New campaign started")
//        logger.info(createCampaignTransaction.toString()) //Print tx id
//        logger.info(createCampaignTransaction.tx.toString())
//
//        logger.info("Donor make a donation to fundraiser $1000")
//        logger.info(acceptDonationTransaction.toString())
//        logger.info(acceptDonationTransaction.tx.toString())
//
//
//
//        // Donor make a donation to fundraiser
//        val makeDonationFlow2= MakeDonation.Initiator(2000.DOLLARS, campaignId2, broadcastToObservers = true)
//        logger.info("MakeDonationFlow: $makeDonationFlow2")
//        val acceptDonationTransaction2 = Donor.startFlow(makeDonationFlow2).getOrThrow()
//        val campaignStateAfterSecondPledge = acceptDonationTransaction2.tx.outputsOfType<Campaign>().single()
//        val campaignStateRefAfterSecondPledge = acceptDonationTransaction2.tx.outRefsOfType<Campaign>().single().ref
//        val secondDonation = acceptDonationTransaction2.tx.outputsOfType<Donation>().single()
//        logger.info("acceptDonationTransaction: $acceptDonationTransaction2")
//        logger.info("secondDonation: $secondDonation")
//
//
//        logger.info("Donor make a donation to fundraiser $2000 (Second Campaign)")
//        logger.info(acceptDonationTransaction2.toString())
//        logger.info(acceptDonationTransaction2.tx.toString())
//
//// Donor make a donation to fundraiser
//        val makeDonationFlow3= MakeDonation.Initiator(3000.DOLLARS, campaignId2, broadcastToObservers = true)
//        logger.info("MakeDonationFlow: $makeDonationFlow3")
//        val acceptDonationTransaction3 = Donor.startFlow(makeDonationFlow3).getOrThrow()
//        val campaignStateAfterThirdledge = acceptDonationTransaction3.tx.outputsOfType<Campaign>().single()
//        val campaignStateRefAfterThirdPledge = acceptDonationTransaction3.tx.outRefsOfType<Campaign>().single().ref
//        val thirdDonation = acceptDonationTransaction3.tx.outputsOfType<Donation>().single()
//        logger.info("thirdDonation: $thirdDonation")
//
//        logger.info("Donor make a donation to fundraiser $3000 (Second Campaign)")
//        logger.info(acceptDonationTransaction3.toString())
//        logger.info(acceptDonationTransaction3.tx.toString())
//
//        //Make receipt flow
////        val makeReceiptFlow = MakeReceipt.Initiator(campaignId)
////        val startReceiptFlow = Fundraiser.startFlow(makeReceiptFlow).getOrThrow()
//
//        logger.info("Start ReceiptFlow Successfully  ")
////        logger.info(makeReceiptFlow.toString())
////        logger.info(startReceiptFlow.tx.toString())
//
////        val x = Recipient.services.vaultService.queryBy<Receipt>().states
////        val j = Donor.services.vaultService.queryBy<Receipt>().states
////        val y = Bank.services.vaultService.queryBy<Receipt>().states
////        val z = Fundraiser.services.vaultService.queryBy<Receipt>().states
////        logger.info("===========================================")
////        logger.info("Recipient Receipt Vault : $x")
////        logger.info("Donor Receipt Vault     : $j")
////        logger.info("Bank Receipt Vault      : $y")
////        logger.info("Fundraiser Receipt Vault: $z")
////        logger.info("===========================================")
////
////        // Get the campaign state from the transaction
////        val campaignStateAfterDonaion = acceptDonationTransaction.tx.outRefsOfType<Campaign>().single().ref
////        val campaignAfterDonaiton = acceptDonationTransaction.tx.outputsOfType<Campaign>().single()
////        val newDonationStateRef = acceptDonationTransaction.tx.outRefsOfType<Donation>().single().ref
////        val newDonation= acceptDonationTransaction.tx.outputsOfType<Donation>().single()
////
////        /**End campaign flow*/
////        val endCampaignFlow = EndCampaign.Initiator(campaignStateRefAfterSecondPledge)
////        val startEndCampaignFlow = Fundraiser.startFlow(endCampaignFlow).getOrThrow()
////        logger.info("###################### Bank #########################")
////        logger.info(Bank.services.getCashBalance(USD).toString())
////        logger.info("###################### Donor #########################")
////        logger.info(Donor.services.getCashBalance(USD).toString())
////        logger.info("###################### Recipient #########################")
////        logger.info(Recipient.services.getCashBalance(USD).toString())
////        logger.info("###################### Fundraiser #########################")
////        logger.info(Fundraiser.services.getCashBalance(USD).toString())
//        network.waitQuiescent()
//    }
//

}