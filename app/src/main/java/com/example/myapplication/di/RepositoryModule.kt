package com.example.myapplication.di

import com.example.myapplication.data.repository.AgentRepository
import com.example.myapplication.data.repository.OrderRepository
import com.example.myapplication.domain.repository.IOrderRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindOrderRepository(orderRepository: OrderRepository): IOrderRepository

    // ⭐ 移除：不需要为 AgentRepository 提供方法，Hilt 会自动注入
}