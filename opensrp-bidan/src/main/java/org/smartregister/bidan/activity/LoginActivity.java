package org.smartregister.bidan.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.smartregister.Context;
import org.smartregister.bidan.R;
import org.smartregister.bidan.application.BidanApplication;
import org.smartregister.bidan.util.Config;
import org.smartregister.domain.LoginResponse;
import org.smartregister.domain.Response;
import org.smartregister.domain.ResponseStatus;
import org.smartregister.event.Listener;
import org.smartregister.bidan.lib.ErrorReportingFacade;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.sync.DrishtiSyncScheduler;
import org.smartregister.util.Log;
import org.smartregister.view.BackgroundAction;
import org.smartregister.view.LockingBackgroundTask;
import org.smartregister.view.ProgressIndicator;
import org.smartregister.bidan.activity.SettingsActivity;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.fabric.sdk.android.Fabric;
import util.uniqueIdGenerator.Generator;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS;
import static org.smartregister.domain.LoginResponse.NO_INTERNET_CONNECTIVITY;
import static org.smartregister.domain.LoginResponse.SUCCESS;
import static org.smartregister.domain.LoginResponse.UNAUTHORIZED;
import static org.smartregister.domain.LoginResponse.UNKNOWN_RESPONSE;
import static org.smartregister.util.Log.logError;
import static org.smartregister.util.Log.logVerbose;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = LoginActivity.class.getSimpleName();
    private Context context;
    private EditText userNameEditText;
    private EditText passwordEditText;
    private ProgressDialog progressDialog;
    public static final String ENGLISH_LOCALE = "en";
    public static final String KANNADA_LOCALE = "kn";
    public static final String BENGALI_LOCALE = "bn";
    public static final String BAHASA_LOCALE = "in";
    public static final String ENGLISH_LANGUAGE = "English";
    public static final String KANNADA_LANGUAGE = "Kannada";
    public static final String Bengali_LANGUAGE = "Bengali";
    public static final String Bahasa_LANGUAGE = "Bahasa";

    public static Generator generator;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logVerbose("Initializing ...");

        try{
            AllSharedPreferences allSharedPreferences = new AllSharedPreferences(getDefaultSharedPreferences(this));
            String preferredLocale = allSharedPreferences.fetchLanguagePreference();
            Resources res = getOpenSRPContext().applicationContext().getResources();
            // Change locale settings in the app.
            DisplayMetrics dm = res.getDisplayMetrics();
            android.content.res.Configuration conf = res.getConfiguration();
            conf.locale = new Locale(preferredLocale);
            res.updateConfiguration(conf, dm);
        }catch(Exception e){

        }
        setContentView(R.layout.login);

        positionViews();

        initializeLoginFields();
        initializeBuildDetails();
        setDoneActionHandlerOnPasswordField();
        initializeProgressDialog();
        setLanguage();

        debugApp();

    }

    private void debugApp() {
        Config config = new Config();
        String uname = null, pwd = null;
        try {
            uname =  config.getCredential("uname", getApplicationContext());
            pwd =  config.getCredential("pwd", getApplicationContext());
        } catch (IOException e) {
            e.printStackTrace();
        }

        LayoutInflater layoutInflater = getLayoutInflater();
        View view = layoutInflater.inflate(R.layout.login, null);
        if (context.userService().hasARegisteredUser()){
            localLogin(view, uname, pwd);
        } else {
            remoteLogin(view, uname, pwd);
        }
    }

    private void positionViews() {
        ImageView loginglogo = (ImageView)findViewById(R.id.login_logo);
        loginglogo.setImageDrawable(getResources().getDrawable(R.drawable.login_logo_bidan));
        context = Context.getInstance().updateApplicationContext(this.getApplicationContext());
//        getActionBar().setTitle("");
//        getActionBar().setIcon(getResources().getDrawable(R.mipmap.logo));
//        getActionBar().setBackgroundDrawable(getResources().getDrawable(R.color.action_bar_background));

    }

    public static Context getOpenSRPContext() {
        return BidanApplication.getInstance().context();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        menu.add("Settings");
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getTitle().toString().equalsIgnoreCase("Settings")){
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeBuildDetails() {
        TextView buildDetailsTextView = (TextView) findViewById(org.smartregister.R.id.login_build);
        try {
            buildDetailsTextView.setText("Version " + getVersion() + ", Built on: " + getBuildDate());
        } catch (Exception e) {
            logError("Error fetching build details: " + e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!context.IsUserLoggedOut()) {
            goToHome();
        }

        fillUserIfExists();
    }

    public void login(final View view) {
        hideKeyboard();
        view.setClickable(false);

        final String userName = userNameEditText.getText().toString();
        final String password = passwordEditText.getText().toString();

        if (context.userService().hasARegisteredUser()) {
            localLogin(view, userName, password);
        } else {
            remoteLogin(view, userName, password);
        }
    }

    private void initializeLoginFields() {
        userNameEditText = ((EditText) findViewById(org.smartregister.R.id.login_userNameText));
        userNameEditText.setRawInputType(InputType.TYPE_CLASS_TEXT);
        passwordEditText = ((EditText) findViewById(org.smartregister.R.id.login_passwordText));
        passwordEditText.setRawInputType(InputType.TYPE_CLASS_TEXT);
    }

    private void setDoneActionHandlerOnPasswordField() {
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    login(findViewById(R.id.login_loginButton));
                }
                return false;
            }
        });
    }

    private void initializeProgressDialog() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(getString(org.smartregister.R.string.loggin_in_dialog_title));
        progressDialog.setMessage(getString(org.smartregister.R.string.loggin_in_dialog_message));
    }

    private void localLogin(View view, String userName, String password) {
        if (context.userService().isValidLocalLogin(userName, password)) {
            localLoginWith(userName, password);

            // Tracking Error
//            ErrorReportingFacade.setUsername("", userName);
//            FlurryAgent.setUserId(userName);
        } else {
            showErrorDialog(getString(org.smartregister.R.string.login_failed_dialog_message));
            view.setClickable(true);
        }
    }

    private void remoteLogin(final View view, final String userName, final String password) {
        tryRemoteLogin(userName, password, new Listener<LoginResponse>() {
            public void onEvent(LoginResponse loginResponse) {
//                ErrorReportingFacade.setUsername("", userName);
//                FlurryAgent.setUserId(userName);
                if (loginResponse == SUCCESS) {
                    remoteLoginWith(userName, password, loginResponse.payload());
                } else {
                    if (loginResponse == null) {
                        showErrorDialog("Login failed. Unknown reason. Try Again");
                    } else {
                        if(loginResponse == NO_INTERNET_CONNECTIVITY){
                            showErrorDialog(getResources().getString(R.string.no_internet_connectivity));
                        }else if (loginResponse == UNKNOWN_RESPONSE){
                            showErrorDialog(getResources().getString(R.string.unknown_response));
                        }else if (loginResponse == UNAUTHORIZED){
                            showErrorDialog(getResources().getString(R.string.unauthorized));
                        }
//                        showErrorDialog(loginResponse.message());
                    }
                    view.setClickable(true);
                }
            }
        });

/*        tryGetUniqueId(userName, password, new Listener<ResponseStatus>() {
            @Override
            public void onEvent(ResponseStatus data) {
                if (data == ResponseStatus.failure) {
                    logError("failed to fetch unique id");
                }
                goToHome();
            }
        });*/
    }

    private void showErrorDialog(String message) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.login_failed_dialog_title))
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                })
                .create();
        dialog.show();
    }

    private void getLocation() {
        tryGetLocation(new Listener<Response<String>>() {
            @Override
            public void onEvent(Response<String> data) {
                if (data.status() == ResponseStatus.success) {
                    context.userService().saveAnmLocation(data.payload());
                }
            }
        });
    }

    private void tryGetLocation(final Listener<Response<String>> afterGet) {
        LockingBackgroundTask task = new LockingBackgroundTask(new ProgressIndicator() {
            @Override
            public void setVisible() { }

            @Override
            public void setInvisible() { Log.logInfo("Successfully get location"); }
        });

        task.doActionInBackground(new BackgroundAction<Response<String>>() {
            @Override
            public Response<String> actionToDoInBackgroundThread() {
                return context.userService().getLocationInformation();
            }

            @Override
            public void postExecuteInUIThread(Response<String> result) {
                afterGet.onEvent(result);
            }
        });
    }

    private void tryRemoteLogin(final String userName, final String password, final Listener<LoginResponse> afterLoginCheck) {
        LockingBackgroundTask task = new LockingBackgroundTask(new ProgressIndicator() {
            @Override
            public void setVisible() {
                progressDialog.show();
            }

            @Override
            public void setInvisible() {
                progressDialog.dismiss();
            }
        });

        task.doActionInBackground(new BackgroundAction<LoginResponse>() {
            public LoginResponse actionToDoInBackgroundThread() {
                return context.userService().isValidRemoteLogin(userName, password);
            }

            public void postExecuteInUIThread(LoginResponse result) {
                afterLoginCheck.onEvent(result);
            }
        });
    }

    private void fillUserIfExists() {
        if (context.userService().hasARegisteredUser()) {
            userNameEditText.setText(context.allSharedPreferences().fetchRegisteredANM());
            userNameEditText.setEnabled(false);
        }
    }

    private void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), HIDE_NOT_ALWAYS);
    }

    private void localLoginWith(String userName, String password) {
        context.userService().localLogin(userName, password);
        LoginActivity.generator = new Generator(context, userName, password);
        goToHome();
        DrishtiSyncScheduler.startOnlyIfConnectedToNetwork(getApplicationContext());
    }

    private void remoteLoginWith(String userName, String password, String userInfo) {
        context.userService().remoteLogin(userName, password, userInfo);
        LoginActivity.generator = new Generator(context, userName, password);
        goToHome();
        DrishtiSyncScheduler.startOnlyIfConnectedToNetwork(getApplicationContext());
    }

    private void goToHome() {
        startActivity(new Intent(this, BidanHomeActivity.class));
        finish();
    }

    private String getVersion() throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        return packageInfo.versionName;
    }

    private String getBuildDate() throws PackageManager.NameNotFoundException, IOException {
        ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(getPackageName(), 0);
        ZipFile zf = new ZipFile(applicationInfo.sourceDir);
        ZipEntry ze = zf.getEntry("classes.dex");
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new java.util.Date(ze.getTime()));
    }

    public static void setLanguage(){
        AllSharedPreferences allSharedPreferences = new AllSharedPreferences(getDefaultSharedPreferences(Context.getInstance().applicationContext()));
        String preferredLocale = allSharedPreferences.fetchLanguagePreference();
        Resources res = Context.getInstance().applicationContext().getResources();
        // Change locale settings in the app.
        DisplayMetrics dm = res.getDisplayMetrics();
        android.content.res.Configuration conf = res.getConfiguration();
        conf.locale = new Locale(preferredLocale);
        res.updateConfiguration(conf, dm);

    }
    public static String switchLanguagePreference() {
        AllSharedPreferences allSharedPreferences = new AllSharedPreferences(getDefaultSharedPreferences(Context.getInstance().applicationContext()));

        String preferredLocale = allSharedPreferences.fetchLanguagePreference();
        if (ENGLISH_LOCALE.equals(preferredLocale)) {
            allSharedPreferences.saveLanguagePreference(BAHASA_LOCALE);
            Resources res = Context.getInstance().applicationContext().getResources();
            // Change locale settings in the app.
            DisplayMetrics dm = res.getDisplayMetrics();
            android.content.res.Configuration conf = res.getConfiguration();
            conf.locale = new Locale(BAHASA_LOCALE);
            res.updateConfiguration(conf, dm);
            return Bahasa_LANGUAGE;
        } else {
            allSharedPreferences.saveLanguagePreference(ENGLISH_LOCALE);
            Resources res = Context.getInstance().applicationContext().getResources();
            // Change locale settings in the app.
            DisplayMetrics dm = res.getDisplayMetrics();
            android.content.res.Configuration conf = res.getConfiguration();
            conf.locale = new Locale(ENGLISH_LOCALE);
            res.updateConfiguration(conf, dm);
            return ENGLISH_LANGUAGE;
        }
    }

    private void tryGetUniqueId(final String username, final String password, final Listener<ResponseStatus> afterGetUniqueId) {
        LockingBackgroundTask task = new LockingBackgroundTask(new ProgressIndicator() {
            @Override
            public void setVisible() {
                progressDialog.show();
            }
            @Override
            public void setInvisible() {
                progressDialog.dismiss();
            }
        });

        task.doActionInBackground(new BackgroundAction<ResponseStatus>() {
            @Override
            public ResponseStatus actionToDoInBackgroundThread() {
                LoginActivity.generator = new Generator(context,username,password);
                LoginActivity.generator.uniqueIdService().syncUniqueIdFromServer(username, password);
                return (LoginActivity.generator.uniqueIdService().getLastUsedId(username, password));
            }

            @Override
            public void postExecuteInUIThread(ResponseStatus result) {
                afterGetUniqueId.onEvent(result);
            }
        });
    }

}

