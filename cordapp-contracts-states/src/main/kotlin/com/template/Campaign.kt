package com.template

import com.template.contractAndstate.EndCampaign
import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.loggerFor
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import java.security.PublicKey
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table
import javax.validation.constraints.Null


// ************
// * Contract *
// ************
class CampaignContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.CampaignContract"
    }
    
    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val campaignCommand = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = campaignCommand.signers.toSet()

        when(campaignCommand.value){
            is Commands.Start -> verifyStrart(tx,setOfSigners)
            is Commands.AcceptDonation -> verifyDonation(tx,setOfSigners)
            is Commands.End ->verifyEnd(tx,setOfSigners)
            is Commands.AcceptReceipt ->verifyReceipt(tx,setOfSigners)
            else -> throw IllegalArgumentException("")
        }
    }

    private fun verifyReceipt(tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {
            println("verifyReceipt")
//        "Making a receipt must be only one input state" using (tx.inputStates.size == 1)
//        "Only one input state must be produced when making a receipt" using (tx.outputStates.size == 1)
//        val campaignOutput = tx.outputsOfType<Campaign>().single()
//        val receiptOutput = tx.outputsOfType<Receipt>().single()
//        "The raised amount must be equal" using (campaignOutput.raised == receiptOutput.amount)
    }
    private fun verifyStrart(tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {
        "No input states should be consumed when creating a campaign." using(tx.inputStates.isEmpty())
        "Only one campaign state should be produced when creating a campaign." using (tx.outputStates.size == 1)
        val campaign = tx.outputStates.single() as Campaign
        "The target field of a recently created campaign should be a positive value." using (campaign.target > Amount(0,campaign.target.token))
        "There raised field must be 0 when starting a campaign." using(campaign.raised == Amount(0,campaign.target.token))
        println("campaign.deadline: ${campaign.deadline} ")
        println("Instant.now:${Instant.now()}")
       // "The campaign deadline must be in the future." using (campaign.deadline > Instant.now())

        "There must be a campaign name." using (campaign.name != "")
        "The campaign must only be signed by fundraiser" using (signers == setOf(campaign.fundraiser.owningKey) )
        "There must be a campaign category" using (campaign.category != "")

    }

    private fun verifyDonation(tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {
        "An accept donation transaction must be only one input state" using (tx.inputStates.size == 1)
        "Two outputs state must be produced when accepting a donation" using (tx.outputStates.size == 2)
        val campaignInput: Campaign = tx.inputsOfType<Campaign>().single()
        val campaignOutput: Campaign = tx.outputsOfType<Campaign>().single()
        val donationOutput: Donation = tx.outputsOfType<Donation>().single()

        val changeInAmountRaised: Amount<Currency> = campaignOutput.raised - campaignInput.raised
        "The donation must be for this campaign" using (donationOutput.campaignReference == campaignOutput.linearId)
        "The raised amount must be updated by the new amount donated" using (changeInAmountRaised == donationOutput.amount)

        "The campaign name cannot be changed when accepting a donation" using (campaignInput.name == campaignOutput.name)
        "The campaign target cannot be changed when accepting a donation" using (campaignInput.target == campaignOutput.target)
        "The fundraiser cannot be changed when accepting a donation" using (campaignInput.fundraiser == campaignOutput.fundraiser)
        "The Recipient cannot be changed when accepting a donation" using (campaignInput.recipient == campaignOutput.recipient)
        "The campaign deadline cannot be changed when accepting a donation" using (campaignInput.deadline == campaignOutput.deadline)
        "The campaign category cannot be changed when accepting a donation" using (campaignInput.category == campaignOutput.category)

        //Assert that donation cannot make after the deadline
        tx.timeWindow?.midpoint?.let {
            "The donation cannot be accepted after the campaign deadline" using (it < campaignOutput.deadline)
        }?: throw java.lang.IllegalArgumentException("A time stamp is required when making a donation")

        // Assert signer
        "The campaign must only be signed by fundraiser" using (signers.single() == campaignOutput.fundraiser.owningKey)

    }

    private fun verifyEnd(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "Only one campaign can end at a time" using (tx.inputsOfType<Campaign>().size == 1)
        "There must be only one campaign output when campaign is ended " using (tx.outputsOfType<Campaign>().size == 1)
        "There must be no donation output when campaign is end" using (tx.outputsOfType<Donation>().isEmpty())
        val campaignInput = tx.inputsOfType<Campaign>().single()
        val donationInputs = tx.inputsOfType<Donation>()
        val cashInputs = tx.inputsOfType<Cash.State>()
        val totalInputStates = 1 + donationInputs.size + cashInputs.size
        "Unrequired state has been added to this transaction" using (tx.inputs.size == totalInputStates)
        "The campaign deadline must have passed before ending the campaign" using (campaignInput.deadline <= Instant.now()) // TODO

        //val zero = Amount.zero(campaignInput.target.token)
        //val sumOfAllDonations = donationInputs.map { (amount) -> amount }.fold(zero) { acc, curr -> acc + curr }

        "The ending campaign must be signed by fundraiser" using (campaignInput.fundraiser.owningKey == signers.single())
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Start : TypeOnlyCommandData(),Commands
        class End : TypeOnlyCommandData(), Commands
        class AcceptDonation: TypeOnlyCommandData(), Commands
        class AcceptReceipt: TypeOnlyCommandData(), Commands
    }
}
// Return public key of participants
fun keysFromParticipants(obligation: ContractState): Set<PublicKey>{
    return obligation.participants.map { it.owningKey }.toSet()
}

// *********
// * State *
// *********
data class Campaign(
        val name: String,
        val target: Amount<Currency>,
        val raised: Amount<Currency> = Amount(0,target.token),
        val remainingAmount: Amount<Currency>,
        val transferAmount: Amount<Currency>,
        val fundraiser: Party,
        val recipient: Party,
        val donor: Party,
        val deadline: Instant,
        val recipientName: String,
        val category: String,
        val description: String,
        val objective: String,
        val status: String,
        override val participants: List<AbstractParty> = listOf(fundraiser,recipient,donor),
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState,SchedulableState{
    override fun supportedSchemas() = listOf(CampaignSchemaV1)
    override fun generateMappedObject(schema: MappedSchema) = CampaignSchemaV1.CampaignEntity(this)
    object CampaignSchemaV1 : MappedSchema(Campaign::class.java, 1, listOf(CampaignEntity::class.java)) {
        @Entity
        @Table(name = "campaigns")
        class CampaignEntity(campaign: Campaign) : PersistentState() {
            @Column
            var name: String = campaign.name
            @Column
            var target: Long = campaign.target.quantity
            @Column
            var raised: Long = campaign.raised.quantity
            @Column
            @Lob
            var fundraiser: ByteArray = campaign.fundraiser.owningKey.encoded
            @Column
            @Lob
            var recipient: ByteArray = campaign.recipient.owningKey.encoded
            @Column
            @Lob
            var donor: ByteArray = campaign.recipient.owningKey.encoded
            @Column
            var deadline: Instant = campaign.deadline
            @Column
            var recipientName: String = campaign.recipientName
            @Column
            var category: String = campaign.category
    }
    }
    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
        val logger = loggerFor<Campaign>()
        logger.info("nextScheduledActivity")
        return ScheduledActivity(flowLogicRefFactory.create(EndCampaign.Initiator::class.java, thisStateRef), deadline)

    }

}
