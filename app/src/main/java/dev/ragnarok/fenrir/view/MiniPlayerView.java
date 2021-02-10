package dev.ragnarok.fenrir.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.res.ResourcesCompat;

import com.squareup.picasso.Transformation;
import com.umerov.rlottie.RLottieImageView;

import java.util.Objects;

import dev.ragnarok.fenrir.R;
import dev.ragnarok.fenrir.model.Audio;
import dev.ragnarok.fenrir.picasso.PicassoInstance;
import dev.ragnarok.fenrir.picasso.transforms.RoundTransformation;
import dev.ragnarok.fenrir.place.PlaceFactory;
import dev.ragnarok.fenrir.player.util.MusicUtils;
import dev.ragnarok.fenrir.settings.Settings;
import dev.ragnarok.fenrir.util.RxUtils;
import dev.ragnarok.fenrir.util.Utils;
import io.reactivex.rxjava3.disposables.Disposable;

import static dev.ragnarok.fenrir.player.util.MusicUtils.mService;
import static dev.ragnarok.fenrir.player.util.MusicUtils.observeServiceBinding;
import static dev.ragnarok.fenrir.util.Objects.nonNull;
import static dev.ragnarok.fenrir.util.Utils.firstNonEmptyString;

public class MiniPlayerView extends FrameLayout implements SeekBar.OnSeekBarChangeListener {

    private Disposable mPlayerDisposable = Disposable.disposed();
    private int mAccountId;
    private RLottieImageView visual;
    private ImageView play_cover;
    private TextView Title;
    private boolean mFromTouch;
    private long mPosOverride = -1;
    private View root;
    private long mLastSeekEventTime;

    public MiniPlayerView(Context context) {
        super(context);
        init();
    }

    public MiniPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MiniPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    protected void init() {
        root = LayoutInflater.from(getContext()).inflate(R.layout.mini_player, this);
        View play = root.findViewById(R.id.item_audio_play);
        play_cover = root.findViewById(R.id.item_audio_play_cover);
        visual = root.findViewById(R.id.item_audio_visual);
        root.setVisibility(MusicUtils.getMiniPlayerVisibility() ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.close_player).setOnClickListener(v -> MusicUtils.next());
        play.setOnClickListener(v -> {
            MusicUtils.playOrPause();
            if (MusicUtils.isPlaying()) {
                Utils.doWavesLottie(visual, true);
                play_cover.setColorFilter(Color.parseColor("#44000000"));
            } else {
                Utils.doWavesLottie(visual, false);
                play_cover.clearColorFilter();
            }
        });
        play.setOnLongClickListener(v -> {
            MusicUtils.stop();
            return true;
        });
        Title = root.findViewById(R.id.mini_artist);
        Title.setOnClickListener(v -> PlaceFactory.getPlayerPlace(mAccountId).tryOpenWith(getContext()));
        Title.setSelected(true);
    }

    private Transformation TransformCover() {
        return new RoundTransformation();
    }

    @DrawableRes
    private int getAudioCoverSimple() {
        return R.drawable.audio_button;
    }

    private void updatePlaybackControls() {
        if (nonNull(play_cover)) {
            if (MusicUtils.isPlaying()) {
                Utils.doWavesLottie(visual, true);
                play_cover.setColorFilter(Color.parseColor("#44000000"));
            } else {
                Utils.doWavesLottie(visual, false);
                play_cover.clearColorFilter();
            }
        }
    }

    private void recvFullAudioInfo() {
        updateVisibility();
        updateNowPlayingInfo();
        updatePlaybackControls();
    }

    private void onServiceBindEvent(@MusicUtils.PlayerStatus int status) {
        switch (status) {
            case MusicUtils.PlayerStatus.UPDATE_TRACK_INFO:
                updateVisibility();
                updateNowPlayingInfo();
                updatePlaybackControls();
                break;
            case MusicUtils.PlayerStatus.UPDATE_PLAY_PAUSE:
                updateVisibility();
                updatePlaybackControls();
                break;
            case MusicUtils.PlayerStatus.SERVICE_KILLED:
                updateVisibility();
                updatePlaybackControls();
                updateNowPlayingInfo();
                break;
            case MusicUtils.PlayerStatus.REPEATMODE_CHANGED:
            case MusicUtils.PlayerStatus.SHUFFLEMODE_CHANGED:
                break;
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateNowPlayingInfo() {
        String artist = MusicUtils.getArtistName();
        String trackName = MusicUtils.getTrackName();
        Title.setText(firstNonEmptyString(artist, " ") + " - " + firstNonEmptyString(trackName, " "));
        if (nonNull(play_cover)) {
            Audio audio = MusicUtils.getCurrentAudio();
            if (audio != null && !Utils.isEmpty(audio.getThumb_image_little())) {
                PicassoInstance.with()
                        .load(audio.getThumb_image_little())
                        .placeholder(Objects.requireNonNull(ResourcesCompat.getDrawable(getResources(), getAudioCoverSimple(), getContext().getTheme())))
                        .transform(TransformCover())
                        .into(play_cover);
            } else {
                PicassoInstance.with().cancelRequest(play_cover);
                play_cover.setImageResource(getAudioCoverSimple());
            }
        }
        //queueNextRefresh(1);
    }

    private void updateVisibility() {
        root.setVisibility(MusicUtils.getMiniPlayerVisibility() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
        if (!fromuser || mService == null) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - mLastSeekEventTime > 250) {
            mLastSeekEventTime = now;

            if (!mFromTouch) {
                // refreshCurrentTime();
                mPosOverride = -1;
            }
        }

        mPosOverride = MusicUtils.duration() * progress / 1000;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mFromTouch = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (mPosOverride != -1) {
            MusicUtils.seek(mPosOverride);
            mPosOverride = -1;
        }

        mFromTouch = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        recvFullAudioInfo();

        mAccountId = Settings.get()
                .accounts()
                .getCurrent();
        mPlayerDisposable = observeServiceBinding()
                .compose(RxUtils.applyObservableIOToMainSchedulers())
                .subscribe(this::onServiceBindEvent);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPlayerDisposable.dispose();
    }
}
