package net.number42.dutchtrains.ui.screen.trip

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import net.number42.dutchtrains.ui.components.TripDetailCard
import net.number42.dutchtrains.ui.theme.AppScreenBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onStartFollowing()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Enable notifications to follow a train")
            }
        }
    }

    fun requestFollowTrain() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            viewModel.onStartFollowing()
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.onStartFollowing()
        } else {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = AppScreenBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppScreenBackground),
                title = { Text("Trip details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshNow() },
            state = pullToRefreshState,
            indicator = {
                if (uiState.isRefreshing || pullToRefreshState.distanceFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = Color(0xFF5B63E6),
                            trackColor = Color(0xFFD7DEFF),
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(uiState.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                    }
                }

                uiState.trip != null -> {
                    val trip = uiState.trip ?: return@PullToRefreshBox
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        Button(
                            onClick = {
                                if (uiState.isFollowing) viewModel.onStopFollowing() else requestFollowTrain()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isFollowing) Color(0xFF4E596F) else Color(0xFF5B63E6),
                                contentColor = Color.White
                            )
                        ) {
                            Text(if (uiState.isFollowing) "Stop following" else "Follow this train")
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                        TripDetailCard(
                            trip = trip,
                            materialsByJourneyRef = uiState.materials,
                            isFollowed = uiState.isFollowing,
                        )
                    }
                }
            }

        }
    }
}
