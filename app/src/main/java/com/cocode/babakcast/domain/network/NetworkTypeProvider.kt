package com.cocode.babakcast.domain.network

fun interface NetworkTypeProvider {
    fun current(): NetworkType
}
