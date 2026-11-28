package com.cocode.babakcast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocode.babakcast.data.model.Provider
import com.cocode.babakcast.data.remote.ProviderCheckOutcome
import com.cocode.babakcast.data.remote.ProviderChecker
import com.cocode.babakcast.data.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The values a provider test sends, as typed in the dialog. */
data class ProviderTestInputs(val providerId: String, val apiKey: String, val model: String, val endpoint: String)

data class ProviderTestState(
    val inputs: ProviderTestInputs? = null,
    val running: Boolean = false,
    val outcome: ProviderCheckOutcome? = null
) {
    /** The result only while the dialog still shows the values it tested. */
    fun outcomeFor(current: ProviderTestInputs): ProviderCheckOutcome? =
        outcome.takeIf { !running && inputs == current }
}

/** Runs the Test button in the provider dialog. Nothing is saved. */
@HiltViewModel
class ProviderTestViewModel @Inject constructor(
    private val checker: ProviderChecker
) : ViewModel() {

    private val _state = MutableStateFlow(ProviderTestState())
    val state: StateFlow<ProviderTestState> = _state.asStateFlow()

    private var job: Job? = null

    fun test(provider: Provider, inputs: ProviderTestInputs) {
        if (_state.value.running) return
        _state.value = ProviderTestState(inputs = inputs, running = true)
        job = viewModelScope.launch {
            val target = ProviderRepository.withOverrides(provider, inputs.model, inputs.endpoint)
            val outcome = checker.check(target, inputs.apiKey)
            _state.value = ProviderTestState(inputs = inputs, running = false, outcome = outcome)
        }
    }

    /** Cancels a running test and forgets the result (the dialog closed). */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = ProviderTestState()
    }
}
