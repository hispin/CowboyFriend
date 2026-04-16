package com.israel.cowboyfriend.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.israel.cowboyfriend.classes.Cow
import com.israel.cowboyfriend.interfaces.CowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddCowViewModel @Inject constructor(
    private val productRepository: CowRepository,
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading
    private val _showSuccessMessage = MutableStateFlow(false)
    val showSuccessMessage: Flow<Boolean> = _showSuccessMessage
    fun onCreateCow(number: Int?, number_mom: Int?, gender: String) {
        if ( number_mom==null || number==null || gender.isEmpty() ||number_mom <= 0|| number <= 0 ) return
        viewModelScope.launch {
            _isLoading.value = true
            val cow =Cow(
                number=number,
                number_mom=number_mom,
                gender=gender
            )
            productRepository.createCow(cow = cow)
            _isLoading.value = false
            _showSuccessMessage.emit(true)
        }
    }

}