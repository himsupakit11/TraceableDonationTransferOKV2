package com.template

import com.template.contractAndstate.EndCampaign
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.contracts.getCashBalance
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger
import java.time.Instant
import kotlin.test.assertEquals

class MakeReceiptTest{
    lateinit var network: MockNetwork
    lateinit var Bank: StartedMockNode
    lateinit var Donor: StartedMockNode
    lateinit var Fundraiser: StartedMockNode
    lateinit var Recipient: StartedMockNode
    @Before
    fun setup(){
        network = MockNetwork(cordappPackages =  listOf("com.template"), threadPerNode =  true)
        Bank = network.createPartyNode(CordaX500Name("Bank","Bangkok","TH"))
        Donor = network.createPartyNode(CordaX500Name("Alice","BKK","TH"))
        Recipient = network.createPartyNode(CordaX500Name("Hospital","PCKK", "TH"))
        Fundraiser = network.createPartyNode(CordaX500Name("PToon","BKK","TH"))

//        listOf(Fundraiser, Bank, Donor, Recipient).forEach { it.registerInitiatedFlow(AutoOfferFlow.RecordTransactionAsObserver::class.java) }
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
    private fun calculatedDeadlineInSeconds(interval: Long) = Instant.now().plusSeconds(interval)
    private val fiveSecondsFromNow: Instant get() = calculatedDeadlineInSeconds(5L)
    private val OneSecondsFromNow: Instant get() = calculatedDeadlineInSeconds(10L)
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
                target = 3000.DOLLARS,
                remainingAmount = 0.DOLLARS,
                transferAmount = 0.DOLLARS,
                fundraiser = Fundraiser.services.myInfo.legalIdentities.first(),
                recipient = Recipient.services.myInfo.legalIdentities.first(),
                donor = Donor.services.myInfo.legalIdentities.first(),
                deadline = OneSecondsFromNow,
                recipientName = "Hospital",
                category = "Charity",
                description = "abcdef",
                objective = "ghjkl",
                bank = Bank.services.myInfo.legalIdentities.first(),
                status = "Available")


        // Start a new campaign
        val flow = AutoOfferFlow.StartCampaign(kaokonlakaoCampaign)
        val createCampaignTransaction: SignedTransaction = Fundraiser.startFlow(flow).getOrThrow()
        network.waitQuiescent()


        // Get the campaign state from the transaction
        val campaignState: Campaign = createCampaignTransaction.tx.outputs.single().data as Campaign
        logger.info("CampaignState: ${campaignState}")
        val campaignId: UniqueIdentifier = campaignState.linearId
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




        // Donor make a donation to fundraiser
        val makeDonationFlow= MakeDonation.Initiator(1200.DOLLARS, campaignId," ")
        logger.info("MakeDonationFlow: $makeDonationFlow")
        val acceptDonationTransaction = Donor.startFlow(makeDonationFlow).getOrThrow()
        logger.info("acceptDonationTransaction: $acceptDonationTransaction")
        // val acceptDonationTransaction2: SignedTransaction = Bank.startFlow(makeDonationFlow).getOrThrow()
        //   logger.info("acceptDonationTransaction: $acceptDonationTransaction")

        //  logger.info("campaignId: $campaignId")
        // logger.info("FundraiserTxId: ${campaignState.fundraiser.owningKey}")
        //logger.info("===========================================")
        logger.info("New campaign started")
        logger.info(createCampaignTransaction.toString()) //Print tx id
        logger.info(createCampaignTransaction.tx.toString())

        logger.info("Donor make a donation to fundraiser $100 ")
        logger.info(acceptDonationTransaction.toString())
        logger.info(acceptDonationTransaction.tx.toString())

//        val makeReceiptFlow = MakeReceipt.Initiator(campaignId)
//        val startReceiptFlow = Fundraiser.startFlow(makeReceiptFlow).getOrThrow()
//
//        logger.info("Start ReceiptFlow Successfully  ")
//
//        logger.info(makeReceiptFlow.toString())
//        logger.info(startReceiptFlow.tx.toString())


        val x = Recipient.services.vaultService.queryBy<Receipt>().states
        val j = Donor.services.vaultService.queryBy<Receipt>().states
        val y = Bank.services.vaultService.queryBy<Receipt>().states
        val z = Fundraiser.services.vaultService.queryBy<Receipt>().states
        logger.info("===========================================")
        logger.info("Recipient Receipt Vault : $x")
        logger.info("Donor Receipt Vault     : $j")
        logger.info("Bank Receipt Vault      : $y")
        logger.info("Fundraiser Receipt Vault: $z")
        logger.info("===========================================")
//    logger.info("Bank make a donation to fundraiser $100 ")
//    logger.info(acceptDonationTransaction2.toString())
//    logger.info(acceptDonationTransaction2.tx.toString())

        // Get the campaign state from the transaction
        val campaignStateAfterDonaion = acceptDonationTransaction.tx.outRefsOfType<Campaign>().single().ref
        val campaignAfterDonaiton = acceptDonationTransaction.tx.outputsOfType<Campaign>().single()
        val newDonationStateRef = acceptDonationTransaction.tx.outRefsOfType<Donation>().single().ref
        val newDonation= acceptDonationTransaction.tx.outputsOfType<Donation>().single()



//    val donor = Donor.services.vaultService.queryBy<Donation>().states
//    logger.info("Donor Donation state Vault: $donor")
//    val bank = Bank.services.vaultService.queryBy<Donation>().states
//    logger.info("Donor Donation state Vault: $bank")
//
//    val donor2 = Donor.services.vaultService.queryBy<Campaign>().states
//    logger.info("Donor Campaign state Vault: $donor2")
//      val fundraiser = Fundraiser.services.vaultService.queryBy<Campaign>().states
//    logger.info("Fundraiser  Campaign state Vault: $fundraiser")
//    val recipient = Recipient.services.vaultService.queryBy<Campaign>().states
//    logger.info("Recipient Campaign state Vault: $recipient")




//    val fundraiserCampaignAfterDonation = Fundraiser.services.validatedTransactions.getTransaction(campaignStateAfterDonaion.txhash)
//        val bankCampaignAfterDonation = Bank.services.validatedTransactions.getTransaction(campaignStateAfterDonaion.txhash)
//        val donorCampaignAfterDonation = Donor.services.validatedTransactions.getTransaction(campaignStateAfterDonaion.txhash)
//        val recipientCampaignAfterDonation = Recipient.services.validatedTransactions.getTransaction(campaignStateAfterDonaion.txhash)
//
//        assertEquals(1,
//                setOf(
//                        campaignAfterDonaiton,
//                        fundraiserCampaignAfterDonation,
//                        bankCampaignAfterDonation,
//                        donorCampaignAfterDonation,
//                        recipientCampaignAfterDonation
//                ).size
//        )
//        val fundraiserNewDonation = Fundraiser.services.validatedTransactions.getTransaction(newDonationStateRef.txhash)
//        val bankNewDonation = Bank.services.validatedTransactions.getTransaction(newDonationStateRef.txhash)
//        val donorNewDonation = Donor.services.validatedTransactions.getTransaction(newDonationStateRef.txhash)
//        val recipientNewDonation = Fundraiser.services.validatedTransactions.getTransaction(newDonationStateRef.txhash)
//
//        assertEquals(1,
//                setOf(
//                newDonation,
//                fundraiserNewDonation,
//                bankNewDonation,
//                donorNewDonation,
//                recipientNewDonation
//                ).size
//        )
//
//        // Only Donor and fundraiser know each other identity
//        assertEquals(Fundraiser.services.myInfo.legalIdentities.first(), Fundraiser.services.identityService.wellKnownPartyFromAnonymous(newDonation.donor))
//        assertEquals(Fundraiser.services.myInfo.legalIdentities.first(), Donor.services.identityService.wellKnownPartyFromAnonymous(newDonation.donor))
//        assertEquals(null,Recipient.services.identityService.wellKnownPartyFromAnonymous(newDonation.donor))
//        assertEquals(null,Donor.services.identityService.wellKnownPartyFromAnonymous(newDonation.donor))
        network.waitQuiescent()
    }



}