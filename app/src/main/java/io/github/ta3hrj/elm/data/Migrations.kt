package io.github.ta3hrj.elm.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema upgrades. Every statement must produce exactly the schema Room expects for the new version
 * (see app/schemas/), otherwise Room refuses to open the database on upgraded devices.
 */
object Migrations {

    /** 0.1.0 -> 0.2.0: TV guide tables and guide columns on playlists. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach(db::execSQL)
        }
    }

    /** 0.2.0 -> 0.3.0: films and series, watch progress, reminders, catch-up fields on channels. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach(db::execSQL)
        }
    }

    /** 0.3.x -> 0.4.0: films and series found in M3U playlists. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_3_4_SQL.forEach(db::execSQL)
        }
    }

    /** 0.4.x -> 0.5.0: channel numbers, parental lock / hidden categories, hidden channels. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_4_5_SQL.forEach(db::execSQL)
            // Existing channels get their list position as number until the next playlist refresh.
            db.execSQL("UPDATE channels SET number = position + 1")
        }
    }

    /** 0.5.x -> 0.6.0: recordings. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_5_6_SQL.forEach(db::execSQL)
        }
    }

    /** 0.7.x -> 0.8.0: own favorite lists and recently watched channels. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_6_7_SQL.forEach(db::execSQL)
        }
    }

    /** 0.8.x -> 0.9.0: watchlist and "continue watching" for films and series. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_7_8_SQL.forEach(db::execSQL)
        }
    }

    /** 0.9.1 -> 0.9.2: profiles (everything personal moves to the main profile) and series recording rules. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_8_9_SQL.forEach(db::execSQL)
        }
    }

    /** 0.9.10 -> 0.9.11: search columns without case and accents (filled at start, see fillSearchIndex). */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_9_10_SQL.forEach(db::execSQL)
        }
    }

    /** 0.9.11 -> 0.9.12: indexes for looking channels up by address and number. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_10_11_SQL.forEach(db::execSQL)
        }
    }

    /** 0.9.13 -> 0.9.14: guide matches chosen by hand, and the guide's channel list to choose from. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_11_12_SQL.forEach(db::execSQL)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
}

internal val MIGRATION_1_2_SQL = listOf(
    "ALTER TABLE `playlists` ADD COLUMN `epgUrl` TEXT",
    "ALTER TABLE `playlists` ADD COLUMN `epgAutoUrls` TEXT",
    "ALTER TABLE `playlists` ADD COLUMN `epgUpdatedAt` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `playlists` ADD COLUMN `epgError` TEXT",
    "ALTER TABLE `playlists` ADD COLUMN `programmeCount` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `playlists` ADD COLUMN `epgLinkedChannels` INTEGER NOT NULL DEFAULT 0",
    "CREATE TABLE IF NOT EXISTS `programmes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `channelKey` TEXT NOT NULL, `start` INTEGER NOT NULL, `stop` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_programmes_playlistId_channelKey_stop` ON `programmes` (`playlistId`, `channelKey`, `stop`)",
    "CREATE TABLE IF NOT EXISTS `epg_links` (`playlistId` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `epgKey` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `streamUrl`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
)

internal val MIGRATION_2_3_SQL = listOf(
    "ALTER TABLE `playlists` ADD COLUMN `serverTimezone` TEXT",
    "ALTER TABLE `playlists` ADD COLUMN `vodUpdatedAt` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `playlists` ADD COLUMN `vodError` TEXT",
    "ALTER TABLE `playlists` ADD COLUMN `movieCount` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `playlists` ADD COLUMN `seriesCount` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `channels` ADD COLUMN `providerId` TEXT",
    "ALTER TABLE `channels` ADD COLUMN `catchupType` TEXT",
    "ALTER TABLE `channels` ADD COLUMN `catchupSource` TEXT",
    "CREATE TABLE IF NOT EXISTS `vod_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `providerId` TEXT NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `poster` TEXT, `rating` TEXT, `year` TEXT, `containerExtension` TEXT, `added` INTEGER, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_vod_items_playlistId_kind_category` ON `vod_items` (`playlistId`, `kind`, `category`)",
    "CREATE INDEX IF NOT EXISTS `index_vod_items_playlistId_kind_position` ON `vod_items` (`playlistId`, `kind`, `position`)",
    "CREATE TABLE IF NOT EXISTS `watch_progress` (`playlistId` INTEGER NOT NULL, `mediaKey` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `mediaKey`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `title` TEXT NOT NULL, `start` INTEGER NOT NULL, `stop` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_playlistId_streamUrl_start` ON `reminders` (`playlistId`, `streamUrl`, `start`)",
)

internal val MIGRATION_3_4_SQL = listOf(
    "ALTER TABLE `vod_items` ADD COLUMN `streamUrl` TEXT",
    "ALTER TABLE `vod_items` ADD COLUMN `seriesKey` TEXT",
    "ALTER TABLE `vod_items` ADD COLUMN `season` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `vod_items` ADD COLUMN `episode` INTEGER NOT NULL DEFAULT 0",
    "CREATE INDEX IF NOT EXISTS `index_vod_items_playlistId_kind_seriesKey` ON `vod_items` (`playlistId`, `kind`, `seriesKey`)",
)

internal val MIGRATION_4_5_SQL = listOf(
    "ALTER TABLE `channels` ADD COLUMN `number` INTEGER NOT NULL DEFAULT 0",
    "CREATE TABLE IF NOT EXISTS `group_flags` (`playlistId` INTEGER NOT NULL, `scope` TEXT NOT NULL, `name` TEXT NOT NULL, `locked` INTEGER NOT NULL DEFAULT 0, `hidden` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`playlistId`, `scope`, `name`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `hidden_channels` (`playlistId` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `streamUrl`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
)

internal val MIGRATION_5_6_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `channelName` TEXT NOT NULL, `title` TEXT NOT NULL, `streamUrl` TEXT NOT NULL, `userAgent` TEXT NOT NULL, `start` INTEGER NOT NULL, `stop` INTEGER NOT NULL, `status` TEXT NOT NULL, `filePath` TEXT, `sizeBytes` INTEGER NOT NULL DEFAULT 0, `error` TEXT, `createdAt` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_recordings_status` ON `recordings` (`status`)",
    "CREATE INDEX IF NOT EXISTS `index_recordings_start` ON `recordings` (`start`)",
)

internal val MIGRATION_6_7_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `favorite_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_favorite_lists_playlistId` ON `favorite_lists` (`playlistId`)",
    "CREATE TABLE IF NOT EXISTS `favorite_list_items` (`listId` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`listId`, `streamUrl`), FOREIGN KEY(`listId`) REFERENCES `favorite_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `recent_channels` (`playlistId` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `watchedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `streamUrl`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
)

internal val MIGRATION_7_8_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `watchlist` (`playlistId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `providerId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `kind`, `providerId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `continue_watching` (`playlistId` INTEGER NOT NULL, `mediaKey` TEXT NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT NOT NULL, `url` TEXT NOT NULL, `poster` TEXT, `seriesItemId` INTEGER NOT NULL, `episodeId` TEXT, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `mediaKey`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
)

internal val MIGRATION_8_9_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `kids` INTEGER NOT NULL, `allowedLive` TEXT, `allowedMovies` TEXT, `allowedSeries` TEXT, `position` INTEGER NOT NULL)",
    "INSERT OR IGNORE INTO profiles (id, name, color, kids, position) VALUES (1, '', -11557889, 0, 0)",
    "CREATE TABLE IF NOT EXISTS `favorites_new` (`profileId` INTEGER NOT NULL DEFAULT 1, `playlistId` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `playlistId`, `streamUrl`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "INSERT INTO `favorites_new` (`profileId`, `playlistId`, `streamUrl`, `addedAt`) SELECT 1, `playlistId`, `streamUrl`, `addedAt` FROM `favorites`",
    "DROP TABLE `favorites`",
    "ALTER TABLE `favorites_new` RENAME TO `favorites`",
    "CREATE TABLE IF NOT EXISTS `watch_progress_new` (`profileId` INTEGER NOT NULL DEFAULT 1, `playlistId` INTEGER NOT NULL, `mediaKey` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `playlistId`, `mediaKey`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "INSERT INTO `watch_progress_new` (`profileId`, `playlistId`, `mediaKey`, `positionMs`, `durationMs`, `updatedAt`) SELECT 1, `playlistId`, `mediaKey`, `positionMs`, `durationMs`, `updatedAt` FROM `watch_progress`",
    "DROP TABLE `watch_progress`",
    "ALTER TABLE `watch_progress_new` RENAME TO `watch_progress`",
    "CREATE TABLE IF NOT EXISTS `recent_channels_new` (`profileId` INTEGER NOT NULL DEFAULT 1, `playlistId` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `watchedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `playlistId`, `streamUrl`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "INSERT INTO `recent_channels_new` (`profileId`, `playlistId`, `streamUrl`, `watchedAt`) SELECT 1, `playlistId`, `streamUrl`, `watchedAt` FROM `recent_channels`",
    "DROP TABLE `recent_channels`",
    "ALTER TABLE `recent_channels_new` RENAME TO `recent_channels`",
    "CREATE TABLE IF NOT EXISTS `watchlist_new` (`profileId` INTEGER NOT NULL DEFAULT 1, `playlistId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `providerId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `playlistId`, `kind`, `providerId`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "INSERT INTO `watchlist_new` (`profileId`, `playlistId`, `kind`, `providerId`, `addedAt`) SELECT 1, `playlistId`, `kind`, `providerId`, `addedAt` FROM `watchlist`",
    "DROP TABLE `watchlist`",
    "ALTER TABLE `watchlist_new` RENAME TO `watchlist`",
    "CREATE TABLE IF NOT EXISTS `continue_watching_new` (`profileId` INTEGER NOT NULL DEFAULT 1, `playlistId` INTEGER NOT NULL, `mediaKey` TEXT NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT NOT NULL, `url` TEXT NOT NULL, `poster` TEXT, `seriesItemId` INTEGER NOT NULL, `episodeId` TEXT, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `playlistId`, `mediaKey`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "INSERT INTO `continue_watching_new` (`profileId`, `playlistId`, `mediaKey`, `title`, `subtitle`, `url`, `poster`, `seriesItemId`, `episodeId`, `positionMs`, `durationMs`, `updatedAt`) SELECT 1, `playlistId`, `mediaKey`, `title`, `subtitle`, `url`, `poster`, `seriesItemId`, `episodeId`, `positionMs`, `durationMs`, `updatedAt` FROM `continue_watching`",
    "DROP TABLE `continue_watching`",
    "ALTER TABLE `continue_watching_new` RENAME TO `continue_watching`",
    "ALTER TABLE `favorite_lists` ADD COLUMN `profileId` INTEGER NOT NULL DEFAULT 1",
    "CREATE INDEX IF NOT EXISTS `index_favorite_lists_profileId` ON `favorite_lists` (`profileId`)",
    "CREATE TABLE IF NOT EXISTS `recording_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `channelName` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_recording_rules_playlistId` ON `recording_rules` (`playlistId`)",
)

internal val MIGRATION_9_10_SQL = listOf(
    "ALTER TABLE `channels` ADD COLUMN `searchName` TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE `vod_items` ADD COLUMN `searchName` TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE `programmes` ADD COLUMN `searchTitle` TEXT NOT NULL DEFAULT ''",
)

internal val MIGRATION_10_11_SQL = listOf(
    "CREATE INDEX IF NOT EXISTS `index_channels_playlistId_streamUrl` ON `channels` (`playlistId`, `streamUrl`)",
    "CREATE INDEX IF NOT EXISTS `index_channels_playlistId_number` ON `channels` (`playlistId`, `number`)",
)

internal val MIGRATION_11_12_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `epg_overrides` (`playlistId` INTEGER NOT NULL, `streamUrl` TEXT NOT NULL, `epgKey` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `streamUrl`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `epg_sources` (`playlistId` INTEGER NOT NULL, `epgKey` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `epgKey`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
)
