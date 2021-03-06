package it.unisannio.greenbusapplication;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.unisannio.greenbusapplication.dto.LoginDTO;
import it.unisannio.greenbusapplication.dto.RouteDTO;
import it.unisannio.greenbusapplication.dto.SessionDTO;
import it.unisannio.greenbusapplication.dto.TicketDTO;
import it.unisannio.greenbusapplication.service.GreenBusService;
import it.unisannio.greenbusapplication.util.Values;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LOGIN_ACTIVITY";
    private static final String sharedPreferencesName = "GreenBusApplication";
    private static String baseUrl;
    private SharedPreferences sharedPreferences;
    private EditText username;
    private EditText password;
    private Button login;
    private Button signIn;

    private List<RouteDTO> routes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sharedPreferences = getSharedPreferences(sharedPreferencesName, MODE_PRIVATE);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        login = findViewById(R.id.login);
        signIn = findViewById(R.id.sign_in);
        baseUrl = Values.localAddress + Values.baseApi;
        Intent fromCaller = getIntent();
        routes = (ArrayList<RouteDTO>) fromCaller.getSerializableExtra(getResources().getString(R.string.routes));

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (username.getText().toString().length() != 0 && password.getText().toString().length() != 0)
                    loginTask(username.getText().toString(), password.getText().toString());
                else
                    Snackbar.make(findViewById(android.R.id.content), getResources().getString(R.string.login_failed), Snackbar.LENGTH_LONG).show();
            }
        });
        signIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignInActivity.class);
                intent.putExtra(getResources().getString(R.string.routes), (Serializable) routes);
                startActivity(intent);
            }
        });
    }

    private void getOneTimeTicketTask() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            Retrofit retrofit = new Retrofit.Builder().baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            GreenBusService greenBusService = retrofit.create(GreenBusService.class);

            String authType = getResources().getString(R.string.authType);
            String jwt = sharedPreferences.getString(getResources().getString(R.string.jwt), null);
            Call<TicketDTO> call = greenBusService.getTicket(authType.concat(" ").concat(jwt));
            Response<TicketDTO> response = null;
            try {
                response = call.execute();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            Response<TicketDTO> finalResponse = response;
            handler.post(() -> {
                if (finalResponse.code() == 200) {
                    Intent intent = new Intent(LoginActivity.this, LicensePlateActivity.class);
                    intent.putExtra(getResources().getString(R.string.routes), (Serializable) routes);
                    intent.putExtra(getResources().getString(R.string.oneTimeTicket), (Serializable) finalResponse.body().getOneTimeTicket());
                    startActivity(intent);
                } else {
                    Log.e(TAG, String.valueOf(finalResponse.code()));
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.server_problem), Toast.LENGTH_LONG).show();
                }
            });
        });

    }

    private void loginTask(String username, String password) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            Retrofit retrofit = new Retrofit.Builder().baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            GreenBusService greenBusService = retrofit.create(GreenBusService.class);
            Call<SessionDTO> call = greenBusService.getTokenForLogin(new LoginDTO(username, password));
            Response<SessionDTO> response = null;

            try {
                response = call.execute();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }

            Response<SessionDTO> finalResponse = response;
            handler.post(() -> {
                if (finalResponse.code() == 200) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(getResources().getString(R.string.jwt), String.valueOf(finalResponse.body().getJwt())).apply();
                    if (finalResponse.body().getRoles().contains(Values.ROLE_DRIVER)) {
                        getOneTimeTicketTask();
                    } else if (finalResponse.body().getRoles().contains(Values.ROLE_PASSENGER)) {
                        Intent intent = new Intent(LoginActivity.this, PassengerMapActivity.class);
                        intent.putExtra(getResources().getString(R.string.routes), (Serializable) routes);
                        startActivity(intent);
                    }
                } else {
                    Snackbar.make(findViewById(android.R.id.content), getResources().getString(R.string.login_failed), Snackbar.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    public void onBackPressed() {
        AlertDialog title = new AlertDialog.Builder(LoginActivity.this)
                .setTitle(getResources().getString(R.string.confirm_exit))
                .setIcon(R.drawable.ic_bus)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(1);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                }).show();
    }
}