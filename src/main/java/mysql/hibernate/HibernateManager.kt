package mysql.hibernate

import core.MainLogger
import mysql.hibernate.entity.GuildEntity
import java.io.IOException
import java.util.*
import javax.persistence.EntityManagerFactory
import javax.persistence.Persistence

object HibernateManager {

    private lateinit var entityManagerFactory: EntityManagerFactory

    @JvmStatic
    @Throws(IOException::class)
    fun connect() {
        val props = Properties()
        props["hibernate.ogm.datastore.host"] = System.getenv("MONGODB_HOST")
        props["hibernate.ogm.datastore.username"] = System.getenv("MONGODB_USER")
        props["hibernate.ogm.datastore.password"] = System.getenv("MONGODB_PASSWORD")
        MainLogger.get().info("Connecting with MongoDB database")
        entityManagerFactory = Persistence.createEntityManagerFactory("lawliet", props)
    }

    @JvmStatic
    fun createEntityManager(): EntityManagerWrapper {
        return EntityManagerWrapper(entityManagerFactory.createEntityManager())
    }

    @JvmStatic
    fun findGuildEntity(guildId: Long): GuildEntity {
        return createEntityManager().findGuildEntity(guildId)
    }

}
