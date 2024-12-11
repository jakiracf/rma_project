package com.example.myapplication

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.awaitResponse
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface SteamApiService {
    @GET("ISteamApps/GetAppList/v2/")
    fun getAllGames(): Call<GameResponse>
}

interface SteamStoreApiService {
    @GET("api/appdetails/")
    fun getAppDetails(@Query("appids") appId: String): Call<Map<String, AppDetailsResponse>>
}

data class GameResponse(
    val applist: AppList
)

data class AppList(
    val apps: List<Game>
)

data class Game(
    val appid: Int = 0,
    val name: String = "",
    val header_image: String = ""
)

data class AppDetailsResponse(
    val data: GameDetails
)

data class GameDetails(
    val name: String,
    val header_image: String
)

object RetrofitInstance {
    val api: SteamApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.steampowered.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SteamApiService::class.java)
    }
}

object RetrofitStoreInstance {
    val api: SteamStoreApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://store.steampowered.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SteamStoreApiService::class.java)
    }
}

class GameViewModel : ViewModel() {
    private val _games = mutableStateOf<List<Game>>(emptyList())
    val games: State<List<Game>> = _games

    val db = FirebaseFirestore.getInstance()

    init {
        fetchGames()
    }

    private fun fetchGames() {
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.getAllGames().awaitResponse()
                if (response.isSuccessful) {
                    response.body()?.let {
                        val gameList = it.applist.apps.filter {
                            game: Game -> game.name.contains("tropico 6", ignoreCase = true)
                        }
                        val detailedGames = gameList.mapNotNull { game ->
                            fetchGameDetails(game.appid)
                        }
                        _games.value = detailedGames
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchGameDetails(appid: Int): Game? {
        return try {
            val response = RetrofitStoreInstance.api.getAppDetails(appid.toString()).awaitResponse()
            if (response.isSuccessful) {
                response.body()?.get(appid.toString())?.data?.let { gameDetails ->
                    Game(appid, gameDetails.name, gameDetails.header_image)
                }
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun addToWishlist(game: Game) {
        db.collection("games").add(game)
            .addOnSuccessListener {
                // Handle success
            }
            .addOnFailureListener {
                // Handle failure
            }
    }

    fun clearWishlist() {
        db.collection("games").get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    db.collection("games").document(document.id).delete()
                }
            }
            .addOnFailureListener {
                // Handle failure
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamGamesApp(navController: NavController) {
    val viewModel: GameViewModel = viewModel()
    val games by viewModel.games
    var searchQuery by remember { mutableStateOf("") }

    Column {
        TopAppBar(
            title = { Text("Steam Games") }
        )
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search games...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (games.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (searchQuery.isEmpty()) {
                val filteredGames = games.filter { it.name.isNotEmpty() }
                GameGrid(games = filteredGames, viewModel = GameViewModel())
            } else {
                val filteredGames = games.filter { it.name.contains(searchQuery, ignoreCase = true) }
                GameGrid(games = filteredGames, viewModel = GameViewModel())
            }
        }
        Button(
            onClick = { navController.navigate("wishlist_screen") },
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp)
        ) {
            Text("My Wishlist")
        }
    }
}

@Composable
fun GameItem(game: Game, viewModel: GameViewModel) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(8.dp)
    ) {
        Image(
            painter = rememberImagePainter(game.header_image),
            contentDescription = "Game image",
            modifier = Modifier
                .height(180.dp)
                .fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            //horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = game.name,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Add to wishlist",
                modifier = Modifier
                    .size(25.dp)
                    .clickable {
                        viewModel.addToWishlist(game)
                        Toast
                            .makeText(context, "Game added to your wishlist!", Toast.LENGTH_SHORT)
                            .show()
                    }
            )
        }
    }
}

@Composable
fun GameGrid(games: List<Game>, viewModel: GameViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(128.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(games) { game ->
            GameItem(game = game, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(navController: NavController, viewModel: GameViewModel) {
    val context = LocalContext.current
    var wishlistGames by remember { mutableStateOf<List<Game>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.db.collection("games").get()
            .addOnSuccessListener { result ->
                val games = result.mapNotNull { it.toObject(Game::class.java) }
                wishlistGames = games
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to load wishlist", Toast.LENGTH_SHORT).show()
            }
    }

    Column {
        TopAppBar(
            title = { Text("My Wishlist") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to main screen")
                }
            },
            actions = {
                Button(
                    onClick = {
                        viewModel.clearWishlist()
                        wishlistGames = emptyList()
                    },
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Text("Clear Wishlist")
                }
            }
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(wishlistGames) { game ->
                GameItem(game = game, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun BackgroundImage(modifier: Modifier) {
    Box(modifier) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = "Background Image",
            contentScale = ContentScale.FillHeight,
            alpha = 0.5F
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

            MyApplicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background

                ) {
                    BackgroundImage(modifier = Modifier)

                    val navController = rememberNavController()
                    //val sensorManager = (LocalContext.current.getSystemService(Context.SENSOR_SERVICE) as SensorManager)

                    NavHost(navController = navController, startDestination = "login_screen") {
                        composable("login_screen") {
                            LoginRegisterScreen(navController = navController)
                        }
                        composable("main_screen") {
                            SteamGamesApp(navController = navController)
                        }
                        composable("wishlist_screen") {
                            val viewModel: GameViewModel = viewModel()
                            WishlistScreen(viewModel = viewModel, navController = navController)
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun LoginRegisterScreen(navController: NavController) {
    var email by remember { mutableStateOf("fila.com3@gmail.com") }
    var password by remember { mutableStateOf("SpeedLink82<") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it }, label = { Text("Email") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { signIn(context, email, password, navController) }) {
            Text("Login")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { register(context, email, password) }) {
            Text("Register")
        }
    }
}

private fun register(context: Context, email: String, password: String) {
    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(context, "Registered successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Registration failed", Toast.LENGTH_SHORT).show()
            }
        }
}

private fun signIn(context: Context, email: String, password: String, navController: NavController) {
    FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(context, "Logged in successfully", Toast.LENGTH_SHORT).show()
                navController.navigate("main_screen")
            } else {
                Toast.makeText(context, "Login failed", Toast.LENGTH_SHORT).show()
            }
        }
}

/*@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val sampleGame = Game(name = "Sample Game")
    GameItem(game = sampleGame, viewModel = GameViewModel())
}*/

