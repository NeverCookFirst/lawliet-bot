package commands.slashadapters.adapters

import commands.Category
import commands.CommandContainer
import commands.CommandManager
import commands.runnables.configurationcategory.*
import commands.runnables.fisherysettingscategory.*
import commands.runnables.informationcategory.*
import commands.runnables.invitetrackingcategory.*
import commands.runnables.moderationcategory.*
import commands.runnables.utilitycategory.*
import commands.slashadapters.Slash
import commands.slashadapters.SlashAdapter
import commands.slashadapters.SlashMeta
import constants.Language
import core.TextManager
import mysql.hibernate.entity.GuildEntity
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import java.util.*

@Slash(
        name = "config",
        descriptionCategory = [Category.CONFIGURATION],
        descriptionKey = "config_desc",
        commandAssociations = [
            LanguageCommand::class, PrefixCommand::class, CommandPermissionsCommand::class, WhiteListCommand::class, CommandManagementCommand::class,
            NSFWFilterCommand::class, SuggestionConfigCommand::class, AlertsCommand::class, ReactionRolesCommand::class, WelcomeCommand::class,
            AutoRolesCommand::class, StickyRolesCommand::class, AutoChannelCommand::class, AutoQuoteCommand::class, MemberCountDisplayCommand::class,
            TriggerDeleteCommand::class, GiveawayCommand::class, TicketCommand::class, ModSettingsCommand::class, InviteFilterCommand::class,
            WordFilterCommand::class, FisheryCommand::class, FisheryRolesCommand::class, VCTimeCommand::class, InviteTrackingCommand::class,
            CustomConfigCommand::class, VoteRewardsCommand::class
        ]
)
class ConfigAdapter : SlashAdapter() {

    public override fun addOptions(commandData: SlashCommandData): SlashCommandData {
        return commandData.addOptions(
                generateOptionData(OptionType.STRING, "command", "config_command", true, true)
        )
    }

    override fun retrieveChoices(event: CommandAutoCompleteInteractionEvent): List<Command.Choice> {
        return retrieveChoices(event.focusedOption.value, event.member!!, event.channel!!.asTextChannel())
    }

    override fun process(event: SlashCommandInteractionEvent, guildEntity: GuildEntity): SlashMeta {
        val userText = event.getOption("command")!!.asString
        val choices = retrieveChoices(userText, event.member!!, event.channel.asTextChannel())
        return if (choices.isNotEmpty()) {
            val clazz = CommandContainer.getCommandMap()[choices[0].asString]
            SlashMeta(clazz!!, collectArgs(event, "command"))
        } else {
            SlashMeta(HelpCommand::class.java, "") { locale: Locale -> TextManager.getString(locale, TextManager.COMMANDS, "slash_error_invalidcommand", userText) }
        }
    }

    private fun retrieveChoices(userText: String, member: Member, textChannel: TextChannel): List<Command.Choice> {
        val choiceList = ArrayList<Command.Choice>()
        for (clazz in commandAssociations()) {
            if (!CommandManager.commandIsTurnedOnEffectively(clazz.java, member, textChannel)) {
                continue
            }

            val category = commands.Command.getCategory(clazz)
            val commandProperties = commands.Command.getCommandProperties(clazz)
            val trigger = commandProperties.trigger

            val triggerList = mutableListOf(trigger)
            triggerList.addAll(commandProperties.aliases)
            for (language in Language.values()) {
                triggerList += TextManager.getString(language.locale, category, "${trigger}_title")
            }
            if (triggerList.any { it.lowercase().contains(userText.lowercase()) }) {
                choiceList += generateChoice(category.id, "${trigger}_title", trigger)
            }
        }

        return choiceList.toList()
                .sortedBy { it.name }
    }

}