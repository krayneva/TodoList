/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.molchanova.todolist.presentation.taskdetail

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.molchanova.todolist.presentation.data.Result

import ru.molchanova.todolist.presentation.Event
import ru.molchanova.todolist.presentation.R
import ru.molchanova.todolist.presentation.data.Task
import ru.molchanova.todolist.presentation.data.source.TasksRepository
import ru.molchanova.todolist.presentation.util.wrapEspressoIdlingResource

/**
 * ViewModel for the Details screen.
 */
class TaskDetailViewModel(
    private val tasksRepository: TasksRepository
) : ViewModel() {

    private val _task = MutableLiveData<Task>()
    val task: LiveData<Task> = _task

    private val _isDataAvailable = MutableLiveData<Boolean>()
    val isDataAvailable: LiveData<Boolean> = _isDataAvailable

    private val _dataLoading = MutableLiveData<Boolean>()
    val dataLoading: LiveData<Boolean> = _dataLoading

    private val _editTaskEvent = MutableLiveData<Event<Unit>>()
    val editTaskEvent: LiveData<Event<Unit>> = _editTaskEvent

    private val _deleteTaskEvent = MutableLiveData<Event<Unit>>()
    val deleteTaskEvent: LiveData<Event<Unit>> = _deleteTaskEvent

    private val _snackbarText = MutableLiveData<Event<Int>>()
    val snackbarText: LiveData<Event<Int>> = _snackbarText

    private val taskId: String?
        get() = _task.value?.id

    // This LiveData depends on another so we can use a transformation.
    val completed: LiveData<Boolean> = Transformations.map(_task) { input: Task? ->
        input?.isCompleted ?: false
    }


    fun deleteTask() = viewModelScope.launch {
        taskId?.let {
            tasksRepository.deleteTask(it)
            _deleteTaskEvent.value = Event(Unit)
        }
    }

    fun editTask() {
        _editTaskEvent.value = Event(Unit)
    }

    fun setCompleted(completed: Boolean) = viewModelScope.launch {
        val task = _task.value ?: return@launch
        if (completed) {
            tasksRepository.completeTask(task)
            showSnackbarMessage(R.string.task_marked_complete)
        } else {
            tasksRepository.activateTask(task)
            showSnackbarMessage(R.string.task_marked_active)
        }
    }

    fun start(taskId: String?, forceRefresh: Boolean = false) {
        if (_isDataAvailable.value == true && !forceRefresh || _dataLoading.value == true) {
            return
        }

        // Show loading indicator
        _dataLoading.value = true

        wrapEspressoIdlingResource {

            viewModelScope.launch {
                if (taskId != null) {
                    tasksRepository.getTask(taskId, false).let { result ->
                        if (result is ru.molchanova.todolist.presentation.data.Result.Success) {
                            onTaskLoaded(result.data)
                        } else {
                            onDataNotAvailable(result)
                        }
                    }
                }
                _dataLoading.value = false
            }
        }
    }

    private fun setTask(task: Task?) {
        this._task.value = task
        _isDataAvailable.value = task != null
    }

    private fun onTaskLoaded(task: Task) {
        setTask(task)
    }

    private fun onDataNotAvailable(result: Result<Task>) {
        _task.value = null
        _isDataAvailable.value = false
    }

    fun refresh() {
        taskId?.let { start(it, true) }
    }

    private fun showSnackbarMessage(@StringRes message: Int) {
        _snackbarText.value = Event(message)
    }
}
