package com.cocode.babakcast.ui.main

sealed class TweetTextEvent {
    data class Copied(val text: String) : TweetTextEvent()
    data class Share(val text: String) : TweetTextEvent()
}
