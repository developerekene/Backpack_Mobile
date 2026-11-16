package com.example.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NotificationItem
import com.example.data.model.Organization
import com.example.data.repository.BackpackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileAlertsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackpackRepository(application)

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications

    private val _organizations = MutableStateFlow<List<Organization>>(emptyList())
    val organizations: StateFlow<List<Organization>> = _organizations

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter

    val unreadCount = MutableStateFlow(0)

    init {
        loadNotifications()
        loadOrganizations()
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            repository.observeNotifications().collectLatest { list ->
                val previousList = _notifications.value
                val newUnreadItems = list.filter { !it.read && previousList.none { prev -> prev.id == it.id } }
                _notifications.value = list
                unreadCount.value = list.count { !it.read }

                newUnreadItems.forEach { item ->
                    com.example.util.NotificationHelper.showPushNotification(getApplication(), item)
                }
            }
        }
    }

    private fun loadOrganizations() {
        viewModelScope.launch {
            repository.observeBackpackSnapshot().collectLatest { snap ->
                _organizations.value = snap.organizations
            }
        }
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun markAsRead(notification: NotificationItem) {
        viewModelScope.launch {
            repository.markNotificationAsRead(notification.id)
            _notifications.value = _notifications.value.map {
                if (it.id == notification.id) it.copy(read = true) else it
            }
            unreadCount.value = _notifications.value.count { !it.read }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            _notifications.value.forEach {
                if (!it.read) markAsRead(it)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _notifications.value = emptyList()
        _organizations.value = emptyList()
        _selectedFilter.value = "All"
        unreadCount.value = 0
    }
}
