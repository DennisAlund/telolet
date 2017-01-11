package se.oddbit.telolet;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.NativeExpressAdView;
import com.google.firebase.crash.FirebaseCrash;

public class AdViewHolder extends RecyclerView.ViewHolder {
    private static final String LOG_TAG = AdViewHolder.class.getSimpleName();
    private NativeExpressAdView mAdView;

    public AdViewHolder(final View rootItemView) {
        super(rootItemView);
        mAdView = (NativeExpressAdView) rootItemView.findViewById(R.id.user_list_item_ad);
    }

    void makeAd() {
        FirebaseCrash.logcat(Log.DEBUG, LOG_TAG, "Binding view holder to new advertisement");
        mAdView.loadAd(new AdRequest.Builder().build());
    }
}
