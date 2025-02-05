package mysql.hibernate.entity

import core.atomicassets.AtomicTextChannel
import core.cache.ServerPatreonBoostCache
import modules.fishery.FisheryStatus
import mysql.hibernate.template.HibernateDiscordInterface
import mysql.hibernate.template.HibernateEmbeddedEntity
import net.dv8tion.jda.api.entities.Role
import java.util.*
import javax.persistence.*

const val FISHERY = "fishery"

@Embeddable
class FisheryEntity : HibernateEmbeddedEntity<GuildEntity>(), HibernateDiscordInterface {

    @Column(name = "$FISHERY.fisheryStatus")
    @Enumerated(EnumType.STRING)
    private var _fisheryStatus: FisheryStatus? = null
    var fisheryStatus: FisheryStatus
        get() = _fisheryStatus ?: FisheryStatus.STOPPED
        set(value) {
            _fisheryStatus = value
        }

    @Column(name = "$FISHERY.treasureChests")
    private var _treasureChests: Boolean? = null
    var treasureChests: Boolean
        get() = _treasureChests ?: true
        set(value) {
            _treasureChests = value
        }

    @Column(name = "$FISHERY.powerUps")
    private var _powerUps: Boolean? = null
    var powerUps: Boolean
        get() = _powerUps ?: true
        set(value) {
            _powerUps = value
        }

    @Column(name = "$FISHERY.fishReminders")
    private var _fishReminders: Boolean? = null
    var fishReminders: Boolean
        get() = _fishReminders ?: true
        set(value) {
            _fishReminders = value
        }

    @Column(name = "$FISHERY.coinGiftLimit")
    private var _coinGiftLimit: Boolean? = null
    var coinGiftLimit: Boolean
        get() = _coinGiftLimit ?: true
        set(value) {
            _coinGiftLimit = value
        }

    @ElementCollection
    var excludedChannelIds: MutableList<Long> = mutableListOf()
    val excludedChannels: MutableList<AtomicTextChannel>
        get() = getAtomicTextChannelList(excludedChannelIds)

    @ElementCollection
    var roleIds: MutableList<Long> = mutableListOf()
    val roles: List<Role>
        get() {
            val deletedRoleIds = mutableListOf<Long>()
            val roles = getAtomicRoleList(roleIds)
                    .mapNotNull {
                        if (it.get().isEmpty) {
                            deletedRoleIds += it.idLong
                        }
                        return@mapNotNull it.get().orElse(null)
                    }
                    .sortedWith(Comparator.comparingInt { obj: Role -> obj.position })

            if (deletedRoleIds.isNotEmpty()) {
                beginTransaction()
                deletedRoleIds.forEach { roleIds -= it }
                commitTransaction()
            }

            return roles
        }

    @Column(name = "$FISHERY.singleRoles")
    private var _singleRoles: Boolean? = null
    var singleRoles: Boolean
        get() = _singleRoles ?: false
        set(value) {
            _singleRoles = value
        }

    var roleUpgradeChannelId: Long? = null
    val roleUpgradeChannel: AtomicTextChannel
        get() = getAtomicTextChannel(roleUpgradeChannelId)

    @Column(name = "$FISHERY.rolePriceMin")
    private var _rolePriceMin: Long? = null
    var rolePriceMin: Long
        get() = _rolePriceMin ?: 50_000L
        set(value) {
            _rolePriceMin = value
        }

    @Column(name = "$FISHERY.rolePriceMax")
    private var _rolePriceMax: Long? = null
    var rolePriceMax: Long
        get() = _rolePriceMax ?: 800_000_000L
        set(value) {
            _rolePriceMax = value
        }

    @Column(name = "$FISHERY.voiceHoursLimit")
    private var _voiceHoursLimit: Int? = null
    var voiceHoursLimit: Int
        get() = _voiceHoursLimit ?: 5
        set(value) {
            _voiceHoursLimit = value
        }
    val voiceHoursLimitEffectively: Int
        get() = if (ServerPatreonBoostCache.get(guildId)) {
            voiceHoursLimit
        } else {
            5
        }

    @Column(name = "$FISHERY.voteRewardsActive")
    private var _voteRewardsActive: Boolean? = null
    var voteRewardsActive: Boolean
        get() = _voteRewardsActive ?: false
        set(value) {
            _voteRewardsActive = value
        }

    var voteRewardsChannelId: Long? = null
    val voteRewardsChannel: AtomicTextChannel
        get() = getAtomicTextChannel(voteRewardsChannelId)

    @Column(name = "$FISHERY.voteRewardsDailyPortionInPercent")
    private var _voteRewardsDailyPortionInPercent: Int? = null
    var voteRewardsDailyPortionInPercent: Int
        get() = _voteRewardsDailyPortionInPercent ?: 25
        set(value) {
            _voteRewardsDailyPortionInPercent = value
        }

    var voteRewardsAuthorization: String? = null


    override fun getGuildId(): Long {
        return hibernateEntity.guildId
    }

}
