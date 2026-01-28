package com.lawanpmo.autoblocklist.di

import android.content.Context
import com.lawanpmo.autoblocklist.data.classifier.UrlClassifier
import com.lawanpmo.autoblocklist.domain.repository.IUrlClassifier
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindUrlClassifier(impl: UrlClassifier): IUrlClassifier
}
