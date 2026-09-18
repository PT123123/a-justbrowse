package com.justbrowse.data.di

import javax.inject.Qualifier

/** 主空间（默认 justbrowse.db）Room 数据库 / DAO */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultSpaceDb

/** 独立空间（justbrowse_private.db）Room 数据库 / DAO */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PrivateSpaceDb