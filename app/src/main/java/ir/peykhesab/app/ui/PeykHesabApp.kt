package ir.peykhesab.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import ir.peykhesab.app.R
import ir.peykhesab.app.AppViewModel
import ir.peykhesab.app.ui.screens.*

private object Routes {
    const val DASHBOARD = "dashboard"
    const val ORDERS = "orders"
    const val DRIVERS = "drivers"
    const val CUSTOMERS = "customers"
    const val REPORTS = "reports"
    const val NEIGHBORHOODS = "neighborhoods"
    const val NEW_ORDER = "new_order"
    const val BACKUP = "backup"
    const val ORDER_DETAIL = "order/{orderId}"
    const val DRIVER_DETAIL = "driver/{driverId}"

    fun order(id: String) = "order/$id"
    fun driver(id: String) = "driver/$id"
}

private data class BottomDestination(val route: String, val title: String, val iconRes: Int)

@Composable
fun PeykHesabApp(vm: AppViewModel) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val bottoms = remember {
        listOf(
            BottomDestination(Routes.DASHBOARD, "خانه", R.drawable.ic_home),
            BottomDestination(Routes.ORDERS, "سفارش‌ها", R.drawable.ic_receipt_long),
            BottomDestination(Routes.DRIVERS, "راننده‌ها", R.drawable.ic_two_wheeler),
            BottomDestination(Routes.CUSTOMERS, "مشتریان", R.drawable.ic_people),
            BottomDestination(Routes.REPORTS, "گزارش", R.drawable.ic_bar_chart)
        )
    }
    val showBottomBar = currentRoute in bottoms.map { it.route }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is AppViewModel.UiEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottoms.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(destination.iconRes), destination.title) },
                            label = { Text(destination.title) },
                            modifier = Modifier.testTag("nav-${destination.route}")
                        )
                    }
                }
            }
        }
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(outerPadding)
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    vm = vm,
                    onNewOrder = { navController.navigate(Routes.NEW_ORDER) },
                    onOrders = { navController.navigate(Routes.ORDERS) },
                    onNeighborhoods = { navController.navigate(Routes.NEIGHBORHOODS) },
                    onDrivers = { navController.navigate(Routes.DRIVERS) },
                    onBackup = { navController.navigate(Routes.BACKUP) }
                )
            }
            composable(Routes.ORDERS) {
                OrdersScreen(
                    vm = vm,
                    onNewOrder = { navController.navigate(Routes.NEW_ORDER) },
                    onOrder = { navController.navigate(Routes.order(it)) }
                )
            }
            composable(Routes.DRIVERS) {
                DriversScreen(vm = vm, onDriver = { navController.navigate(Routes.driver(it)) })
            }
            composable(Routes.CUSTOMERS) { CustomersScreen(vm) }
            composable(Routes.REPORTS) { ReportsScreen(vm) }
            composable(Routes.NEIGHBORHOODS) { NeighborhoodsScreen(vm) { navController.popBackStack() } }
            composable(Routes.BACKUP) { BackupScreen(vm) { navController.popBackStack() } }
            composable(Routes.NEW_ORDER) {
                NewOrderScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onCreated = { created ->
                        navController.navigate(Routes.order(created.id)) {
                            popUpTo(Routes.NEW_ORDER) { inclusive = true }
                        }
                    },
                    onManageCustomers = { navController.navigate(Routes.CUSTOMERS) },
                    onManageDrivers = { navController.navigate(Routes.DRIVERS) },
                    onManageNeighborhoods = { navController.navigate(Routes.NEIGHBORHOODS) }
                )
            }
            composable(
                route = Routes.ORDER_DETAIL,
                arguments = listOf(navArgument("orderId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("orderId").orEmpty()
                OrderDetailScreen(
                    vm = vm,
                    orderId = id,
                    onBack = { navController.popBackStack() },
                    onNewOrder = { navController.navigate(Routes.NEW_ORDER) }
                )
            }
            composable(
                route = Routes.DRIVER_DETAIL,
                arguments = listOf(navArgument("driverId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("driverId").orEmpty()
                DriverDetailScreen(
                    vm = vm,
                    driverId = id,
                    onBack = { navController.popBackStack() },
                    onOrder = { navController.navigate(Routes.order(it)) },
                    onNewOrder = { navController.navigate(Routes.NEW_ORDER) }
                )
            }
        }
    }
}
