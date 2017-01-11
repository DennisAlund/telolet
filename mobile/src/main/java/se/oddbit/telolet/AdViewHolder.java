package se.oddbit.telolet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.NativeExpressAdView;
import com.google.firebase.crash.FirebaseCrash;

import static io.fabric.sdk.android.services.common.CommonUtils.md5;

public class AdViewHolder extends RecyclerView.ViewHolder {
    private static final String LOG_TAG = AdViewHolder.class.getSimpleName();

    private final NativeExpressAdView mNativeExpressAdView;

    public AdViewHolder(final Context context, final ViewGroup parent, final View rootItemView) {
        super(rootItemView);

        // Calculate the width of the view, because it's necessary for the ad to display properly
        final int density = Math.round(context.getResources().getDisplayMetrics().density);
        int parentDpWidth = parent.getWidth() / density;

        final CardView cardView = (CardView) rootItemView.findViewById(R.id.user_list_item_ad_card);
        mNativeExpressAdView = new NativeExpressAdView(context);
        mNativeExpressAdView.setAdSize(new AdSize(parentDpWidth, 128));
        mNativeExpressAdView.setAdUnitId(context.getString(R.string.banner_ad_unit_id));
        cardView.addView(mNativeExpressAdView);

        final AdRequest.Builder adBuilder = new AdRequest.Builder();
        if (BuildConfig.DEBUG) {
            @SuppressLint("HardwareIds")
            final String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            final String idHash = md5(androidId).toUpperCase();
            FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Adding current device id for adMob test device config: " + idHash);
            adBuilder.addTestDevice(idHash);
            adBuilder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);
        }
        mNativeExpressAdView.loadAd(adBuilder.build());
    }
}
