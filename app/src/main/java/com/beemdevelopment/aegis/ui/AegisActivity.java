package com.beemdevelopment.aegis.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.beemdevelopment.aegis.AegisApplication;
import com.beemdevelopment.aegis.Preferences;
import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.Theme;
import com.beemdevelopment.aegis.ThemeMap;
import com.beemdevelopment.aegis.ui.dialogs.Dialogs;
import com.beemdevelopment.aegis.vault.VaultManagerException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

public abstract class AegisActivity extends AppCompatActivity implements AegisApplication.LockListener {
    private AegisApplication _app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        _app = (AegisApplication) getApplication();

        // set the theme and locale before creating the activity
        Preferences prefs = getPreferences();
        onSetTheme();
        setLocale(prefs.getLocale());
        super.onCreate(savedInstanceState);

        // if the app was killed, relaunch MainActivity and close everything else
        if (savedInstanceState != null && isOrphan()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // set FLAG_SECURE on the window of every AegisActivity
        if (getPreferences().isSecureScreenEnabled()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        // register a callback to listen for lock events
        _app.registerLockListener(this);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        _app.unregisterLockListener(this);
        super.onDestroy();
    }

    @CallSuper
    @Override
    protected void onResume() {
        super.onResume();
        _app.setBlockAutoLock(false);
    }

    @Override
    public void onLocked(boolean userInitiated) {
        setResult(RESULT_CANCELED, null);
        try {
            Method method = Activity.class.getDeclaredMethod("finish", int.class);
            method.setAccessible(true);
            method.invoke(this, 2); // FINISH_TASK_WITH_ACTIVITY = 2
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            finishAndRemoveTask();
        }
    }

    protected AegisApplication getApp() {
        return _app;
    }

    protected Preferences getPreferences() {
        return _app.getPreferences();
    }

    /**
     * Called when the activity is expected to set its theme.
     */
    protected void onSetTheme() {
        setTheme(ThemeMap.DEFAULT);
    }

    /**
     * Sets the theme of the activity. The actual style that is set is picked from the
     * given map, based on the theme configured by the user.
     */
    protected void setTheme(Map<Theme, Integer> themeMap) {
        int theme = themeMap.get(getConfiguredTheme());
        setTheme(theme);
    }

    protected Theme getConfiguredTheme() {
        Theme theme = getPreferences().getCurrentTheme();

        if (theme == Theme.SYSTEM || theme == Theme.SYSTEM_AMOLED) {
            int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                theme = theme == Theme.SYSTEM_AMOLED ? Theme.AMOLED : Theme.DARK;
            } else {
                theme = Theme.LIGHT;
            }
        }

        return theme;
    }

    protected void setLocale(Locale locale) {
        Locale.setDefault(locale);

        Configuration config = new Configuration();
        config.locale = locale;

        this.getResources().updateConfiguration(config, this.getResources().getDisplayMetrics());
    }

    protected boolean saveVault(boolean backup) {
        try {
            getApp().getVaultManager().save(backup);
            return true;
        } catch (VaultManagerException e) {
            Toast.makeText(this, getString(R.string.saving_error), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /**
     * Reports whether this Activity instance has become an orphan. This can happen if
     * the vault was locked by an external trigger while the Activity was still open.
     */
    protected boolean isOrphan() {
        return !(this instanceof MainActivity) && !(this instanceof AuthActivity) && !(this instanceof IntroActivity) && _app.isVaultLocked();
    }

    public static class Helper {
        private Helper() {

        }

        /**
         * Starts an external activity, temporarily blocks automatic lock of Aegis and
         * shows an error dialog if the target activity is not found.
         */
        public static void startExtActivityForResult(Activity activity, Intent intent, int requestCode) {
            AegisApplication app = (AegisApplication) activity.getApplication();
            app.setBlockAutoLock(true);

            try {
                activity.startActivityForResult(intent, requestCode, null);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();

                if (isDocsAction(intent.getAction())) {
                    Dialogs.showErrorDialog(activity, R.string.documentsui_error, e);
                } else {
                    throw e;
                }
            }
        }

        /**
         * Starts an external activity, temporarily blocks automatic lock of Aegis and
         * shows an error dialog if the target activity is not found.
         */
        public static void startExtActivity(Fragment fragment, Intent intent) {
            startExtActivityForResult(fragment, intent, -1);
        }

        /**
         * Starts an external activity, temporarily blocks automatic lock of Aegis and
         * shows an error dialog if the target activity is not found.
         */
        public static void startExtActivityForResult(Fragment fragment, Intent intent, int requestCode) {
            AegisApplication app = (AegisApplication) fragment.getActivity().getApplication();
            app.setBlockAutoLock(true);

            try {
                fragment.startActivityForResult(intent, requestCode, null);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();

                if (isDocsAction(intent.getAction())) {
                    Dialogs.showErrorDialog(fragment.getContext(), R.string.documentsui_error, e);
                } else {
                    throw e;
                }
            }
        }

        private static boolean isDocsAction(@Nullable String action) {
            return action != null && (action.equals(Intent.ACTION_GET_CONTENT)
                    || action.equals(Intent.ACTION_CREATE_DOCUMENT)
                    || action.equals(Intent.ACTION_OPEN_DOCUMENT)
                    || action.equals(Intent.ACTION_OPEN_DOCUMENT_TREE));
        }
    }
}
