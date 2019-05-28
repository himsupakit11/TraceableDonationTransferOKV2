package com.template.webserver

import com.sun.org.apache.regexp.internal.RE
import com.template.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.filterStatesOfType
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import org.hibernate.criterion.Distinct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.Instant
import java.util.*
import javax.validation.constraints.Null
import javax.ws.rs.QueryParam
import net.corda.finance.*
import net.corda.finance.contracts.asset.Cash

@RestController
@RequestMapping("/api")
class Controller {
    companion object {
        private val logger = contextLogger()
    }
    @CrossOrigin
    private fun getCampaignLink(campaign: Campaign) = "/api/campaigns/" + campaign.linearId
    @CrossOrigin
    private fun getCampaignByRef(ref: String): Campaign? {
        val vault = rpc.vaultQueryBy<Campaign>().states
        val states = vault.filterStatesOfType<Campaign>().filter { it.state.data.linearId.toString() == ref }
        return if (states.isEmpty()) null else {
            val campaigns = states.map { it.state.data }
            return if (campaigns.isEmpty()) null else campaigns[0]
        }
    }

    @Autowired
    lateinit var rpc: CordaRPCOps
    @CrossOrigin
    private fun getAvailableCampaign(): Array<Campaign> {
        logger.info("getAvailableCampaign")
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Campaign>(generalCriteria).states.filter { it.state.data.status == "Available" }
        val states = vault.filterStatesOfType<Campaign>()
        return states.map { it.state.data }.toTypedArray()
    }

    private fun getOutOfDateCampaign(): Array<Campaign> {
        logger.info("getAvailableCampaign")
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Campaign>(generalCriteria).states.filter { it.state.data.status == "Out Of Date" }
        val states = vault.filterStatesOfType<Campaign>()
        return states.map { it.state.data }.toTypedArray()
    }
    private fun getAllCampaign(): Array<Campaign> {
        logger.info("getAvailableCampaign")
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
        val vault = rpc.vaultQueryBy<Campaign>(generalCriteria).states
        val states = vault.filterStatesOfType<Campaign>()
        return states.map { it.state.data }.toTypedArray()
    }
    @CrossOrigin
    // Get comsumed and unconsumed campaign state
    @GetMapping("/campaigns/allCampaigns")
    fun fetchAllCampaign(): Array<Campaign> = getAllCampaign()


    @CrossOrigin
    // Get available campaign
    @GetMapping("/campaigns")
    fun fetchAvailableCampaign(): Array<Campaign> = getAvailableCampaign()

    @CrossOrigin
    // Get out of date campaign
    @GetMapping("/campaigns/outOfDateCampaign")
    fun fetchOutOfDateCampaign(): Array<Campaign> = getOutOfDateCampaign()

    /**Run by fundraiser*/
    //Start campaign flow
    @CrossOrigin
    @PostMapping("/campaigns")
    fun storeCampaign(@RequestBody newCampaign: Campaign): ResponseEntity<Any?> {
        return try {
            logger.info("linearId: ${newCampaign.linearId.id}")
            logger.info("externalId: ${newCampaign.linearId.externalId}")
            logger.info("NewCampaign : $newCampaign")
            rpc.startFlow(AutoOfferFlow::StartCampaign, newCampaign).returnValue.getOrThrow()
            logger.info("Create campaign successfully")
            ResponseEntity.created(URI.create(getCampaignLink(newCampaign))).build()
        } catch (ex: Exception) {
            logger.info("Exception when creating deal: $ex", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.toString())
        }
    }
    //Get campaign by campaign linear id
    @CrossOrigin
    @GetMapping("/campaigns/{ref:.+}")
    fun fetchCampagin(@PathVariable ref: String?): ResponseEntity<Any?> {
        val campaign = getCampaignByRef(ref!!)
        logger.info("ref $ref")
        return if (campaign == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity.ok(campaign)
        }

    }
    ///////////////////*  Query finished campaign and successful campaign *//////////////////////////////////////
    @CrossOrigin
    private fun getFinishedAndSuccessfulCampaign(): Array<Campaign>  {
        logger.info("getFinishedAndSuccessfulCampaign")
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Campaign>(generalCriteria).states.filter {it.state.data.status == "Out Of Date"
                && it.state.data.raised >= it.state.data.target}
        logger.info("getFinishedAndSuccessfulCampaign State: $vault")
        val states = vault.filterStatesOfType<Campaign>()
        return states.map { it.state.data }.toTypedArray()
    }
    @CrossOrigin
    @GetMapping("/campaigns/FinishedAndSuccessfulCampaign")
    fun endAndsuccessCampagin(): Array<Campaign>  = getFinishedAndSuccessfulCampaign()
    /////////////////////////////////////////////////////////


    ///////////////////*  Query finished campaign and failed campaign  *//////////////////////////////////////
    @CrossOrigin
    private fun getFinishedAndFailedCampaign(): List<Campaign> {
        logger.info("getFinishedAndFailedCampaign")
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Campaign>(generalCriteria).states.filter { it.state.data.status == "Out Of Date"
                && it.state.data.raised < it.state.data.target}
        logger.info("getFinishedAndFailedCampaign State: $vault")
        val states = vault.filterStatesOfType<Campaign>()
        return states.map { it.state.data }.distinct()
    }
    @CrossOrigin
    @GetMapping("/campaigns/FinishedAndFailedCampaign")
    fun finishedAndFailedCampaign(): List<Campaign> = getFinishedAndFailedCampaign()
    /////////////////////////////////////////////////////////

    ///////////////////*  Query UNCONSUMED campaign state *//////////////////////////////////////
    @CrossOrigin
    private fun getUNCONSUMEDState(): List<Campaign> {
        logger.info("getUNCONSUMEDState")
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Campaign>(generalCriteria).states
        val states = vault.filterStatesOfType<Campaign>()
        return states.map { it.state.data }.distinct()
    }
    @CrossOrigin
    @GetMapping("/campaigns/UNCONSUMEDCampaign")
    fun campaginUNCONSUMEDState(): List<Campaign> = getUNCONSUMEDState()
    /////////////////////////////////////////////////////////


    @GetMapping("/campaigns/networksnapshot")
    fun fetchDeal() = rpc.networkMapSnapshot().toString()
    /********************************************************************************************************/



    /**************************************Donor make donation*****************************************/
    private fun getDonationLink(donation: Donation) = "/api/donations/" + donation.linearId

    private fun getDonationByRef(ref: String): Donation? {
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Donation>(generalCriteria).states
        val states = vault.filterStatesOfType<Donation>().filter { it.state.data.linearId.toString() == ref }
        return if (states.isEmpty()) null else {
            val donations = states.map { it.state.data }
            return if (donations.isEmpty()) null else donations[0]
        }
    }
    private fun getDonationByCampaignRef(ref: String): List<Donation>?{
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Donation>(generalCriteria).states
        val states = vault.filterStatesOfType<Donation>().filter { it.state.data.campaignReference.toString() == ref }
        logger.info("donation states: $states")
        return if (states.isEmpty()) null else {
            val donations = states.map { it.state.data }
            val donationSize = states.map { it.state.data }.size
            logger.info("donations: $donations")
            return if (donations.isEmpty())
                null else donations
        }
    }
    private fun getAllDonation(): Array<Donation> {
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Donation>(generalCriteria).states
        val states = vault.filterStatesOfType<Donation>()
        return states.map { it.state.data }.toTypedArray()
    }

    @CrossOrigin
    @GetMapping("/donations")
    fun fetchDonation(): Array<Donation> = getAllDonation()

    private fun getConsumedAndUnconsumedDonation(): Array<Donation> {
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
        val vault = rpc.vaultQueryBy<Donation>(generalCriteria).states
        val states = vault.filterStatesOfType<Donation>()
        return states.map { it.state.data }.toTypedArray()
    }
    @CrossOrigin
    @GetMapping("/donations/allDonations")
    fun fetchAllDonation(): Array<Donation> = getConsumedAndUnconsumedDonation()
    /**Run by donor*/
    //Start donation flow
    @CrossOrigin
    @PostMapping("/donations")
    fun storeDonation(@QueryParam(value = "id") id: String,
                      @QueryParam(value = "amount") amount: String,
                      @QueryParam(value = "currency") currency: String,
                      @QueryParam(value = "paymentMethod") paymentMethod: String): ResponseEntity<Any?> {
        return try {
            logger.info("id : $id")
            logger.info("amount : $amount")
            logger.info("currency: $currency")
            val campaignReference = UniqueIdentifier.fromString(id)
            logger.info("campaignReference: $campaignReference")
            val settleAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))
//            logger.info("settleAmount : $settleAmount")
            rpc.startFlow(MakeDonation::Initiator,settleAmount,campaignReference,paymentMethod).returnValue.getOrThrow()
            val donationStateAndRef = rpc.vaultQueryBy<Donation>().states.get(0)
            val donationState = donationStateAndRef.state.data
            logger.info("donationLinearId : ${donationState.linearId}")
//            val donationLinearId = vault.state.data.linearId.toString()
            logger.info("Donate fund successfully")
            ResponseEntity.created(URI.create(getDonationLink(donationState))).build()
        } catch (ex: Exception) {
            logger.info("Exception when creating deal: $ex", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.toString())
        }
    }

    //Get donationState by donation linear id
    @CrossOrigin
    @GetMapping("/donations/{ref:.+}")
    fun fetchDonation(@PathVariable ref: String?): ResponseEntity<Any?> {
        val donation = getDonationByRef(ref!!)
        logger.info("ref $ref")
        return if (donation == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity.ok(donation)
        }

    }


    //Query donationState by campaign id
    @CrossOrigin
    @GetMapping("/donations/donationsForCampaign/{ref:.+}")
    fun fetchDonationByCampaignId(@PathVariable ref: String?): ResponseEntity<Any?> {
        logger.info("fetchDonationByCampaignId")
        val donation = getDonationByCampaignRef(ref!!)
        logger.info("fetchDonationByCampaignId donation: $donation")
        logger.info("ref $ref")
        return if (donation == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity.ok(donation)
        }

    }

    /********************************************************************************************************/



    /***************************************(Receipt)Fundraiser transfer fund to recipient*****************************************/

    private fun getReceiptLink(receipt: Receipt) = "/api/receipts/" + receipt.linearId

    private fun getReceiptByRef(ref: String): Receipt? {

        val vault = rpc.vaultQueryBy<Receipt>().states
        val states = vault.filterStatesOfType<Receipt>().filter { it.state.data.linearId.toString() == ref }
        return if (states.isEmpty()) null else {
            val receipts = states.map { it.state.data }
            return if (receipts.isEmpty()) null else receipts[0]
        }
    }
    private fun getAllReceipt(): Array<Receipt> {
        val vault = rpc.vaultQueryBy<Receipt>().states
        val states = vault.filterStatesOfType<Receipt>()
        return states.map { it.state.data }.toTypedArray()
    }

    private fun getReceiptByCampaignRef(ref: String): List<Receipt>?{
        val vault = rpc.vaultQueryBy<Receipt>().states
        val states = vault.filterStatesOfType<Receipt>().filter { it.state.data.campaignReference.toString() == ref }
        logger.info("donation states: $states")
        return if (states.isEmpty()) null else {
            val receipts = states.map { it.state.data }
            logger.info("receipts: $receipts")
            return if (receipts.isEmpty())
                null else receipts
        }
    }
    @CrossOrigin
    @GetMapping("/receipts")
    fun fetchRecept(): Array<Receipt> = getAllReceipt()

    /**Run by fundraiser*/
    //Start receipt flow
    @CrossOrigin
    @PostMapping("/receipts")
    fun storeReceipt(@QueryParam(value = "id") id: String,
                     @QueryParam(value = "amount") amount: String,
                     @QueryParam(value = "currency") currency: String) : ResponseEntity<Any?> {
        return try {
            logger.info("id : $id")
            logger.info("amount : $amount")
            logger.info("currency: $currency")
            val campaignReference = UniqueIdentifier.fromString(id)
            logger.info("campaignReference: $campaignReference")
            val settleAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))
//            logger.info("settleAmount : $settleAmount")
            rpc.startFlow(MakeReceipt::Initiator,campaignReference,settleAmount).returnValue.getOrThrow()
            val receiptStateAndRef = rpc.vaultQueryBy<Receipt>().states.get(0)
            val receiptState = receiptStateAndRef.state.data
            logger.info("receiptLinearId : ${receiptState.linearId}")
//            val donationLinearId = vault.state.data.linearId.toString()
            logger.info("transfer money to recipient successfully")
            ResponseEntity.created(URI.create(getReceiptLink(receiptState))).build()
        } catch (ex: Exception) {
            logger.info("Exception when creating deal: $ex", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.toString())
        }
    }


    //Get receipt by receiptId
    @CrossOrigin
    @GetMapping("/receipts/{ref:.+}")
    fun fetchReceipt(@PathVariable ref: String?): ResponseEntity<Any?> {
        val donation = getReceiptByRef(ref!!)
        logger.info("ref $ref")
        return if (donation == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity.ok(donation)
        }

    }
    //Query receiptState by campaign id
    @CrossOrigin
    @GetMapping("/receipts/receiptByCampaignId/{ref:.+}")
    fun fetchReceiptByCampaignId(@PathVariable ref: String?): ResponseEntity<Any?> {
        logger.info("fetchReceiptByCampaignId")
        val donation = getReceiptByCampaignRef(ref!!)
        logger.info("fetchDonationByCampaignId donation: $donation")
        logger.info("ref $ref")
        return if (donation == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity.ok(donation)
        }

    }
    /********************************************************************************************************/

    /***************************************Cash State*****************************************/
    ///////////////////*  Query cash state *//////////////////////////////////////
    @CrossOrigin
    private fun getCashState(): Array<Cash.State> {
        logger.info("getUNCONSUMEDState")
//         val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Cash.State>().states
        val states = vault.filterStatesOfType<Cash.State>()
        return states.map { it.state.data }.toTypedArray()
    }
    @CrossOrigin
    @GetMapping("/cash/cashState")
    fun cashState(): Array<Cash.State> = getCashState()
    /////////////////////////////////////////////////////////
    /********************************************************************************************************/

    /***************************************Bank get all state *****************************************/
    private fun bankGetAllCampaigns(): Array<Campaign> {
        logger.info("bankGetAllCampaigns")
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Campaign>(generalCriteria).states
        val states = vault.filterStatesOfType<Campaign>()
        return states.map { it.state.data }.toTypedArray()
    }

    private fun bankGetAllDonations(): Array<Donation> {
        logger.info("bankGetAllDonations")
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Donation>(generalCriteria).states
        val states = vault.filterStatesOfType<Donation>()
        return states.map { it.state.data }.toTypedArray()
    }
    private fun bankGetAllreceipts(): Array<Receipt> {
        logger.info("bankGetAllreceipts")
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val vault = rpc.vaultQueryBy<Receipt>(generalCriteria).states
        val states = vault.filterStatesOfType<Receipt>()
        return states.map { it.state.data }.toTypedArray()
    }
    @CrossOrigin
    @GetMapping("/bank/Allcampaigns")
    fun fetchAllCampaigns(): Array<Campaign> = bankGetAllCampaigns()

    @CrossOrigin
    @GetMapping("/bank/Alldonations")
    fun fetchAllDonations(): Array<Donation> = bankGetAllDonations()

    @CrossOrigin
    @GetMapping("/bank/Allreceipts")
    fun fetchAllReceipts(): Array<Receipt> = bankGetAllreceipts()

}
