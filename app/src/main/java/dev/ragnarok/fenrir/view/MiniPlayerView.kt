package dev.ragnarok.fenrir.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.crazylegend.kotlinextensions.views.setTextWithAnimation
import com.squareup.picasso.Transformation
import com.umerov.rlottie.RLottieImageView
import dev.ragnarok.fenrir.R
import dev.ragnarok.fenrir.picasso.PicassoInstance.Companion.with
import dev.ragnarok.fenrir.picasso.transforms.PolyTransformation
import dev.ragnarok.fenrir.place.PlaceFactory
import dev.ragnarok.fenrir.player.util.MusicUtils
import dev.ragnarok.fenrir.player.util.MusicUtils.PlayerStatus
import dev.ragnarok.fenrir.settings.Settings
import dev.ragnarok.fenrir.util.Objects
import dev.ragnarok.fenrir.util.RxUtils
import dev.ragnarok.fenrir.util.Utils
import io.reactivex.rxjava3.disposables.Disposable

class MiniPlayerView : FrameLayout {
    private var mPlayerDisposable = Disposable.disposed()
    private var mAccountId = 0
    private lateinit var visual: RLottieImageView
    private lateinit var play_cover: ImageView
    private lateinit var Title: TextView
    private lateinit var root: View

    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {
        init()
    }

    fun init() {
        root = LayoutInflater.from(context).inflate(R.layout.mini_player, this)
        val play = root.findViewById<View>(R.id.item_audio_play)
        play_cover = root.findViewById(R.id.item_audio_play_cover)
        visual = root.findViewById(R.id.item_audio_visual)
        root.isVisible = MusicUtils.getMiniPlayerVisibility()
        val close_button = root.findViewById<ImageView>(R.id.close_player)
        close_button.setOnClickListener { MusicUtils.next() }
        close_button.setOnLongClickListener {
            MusicUtils.previous(context)
            true
        }
        play.setOnClickListener { v: View? ->
            MusicUtils.playOrPause()
            if (MusicUtils.isPlaying()) {
                Utils.doWavesLottie(visual, true)
                play_cover.setColorFilter(Color.parseColor("#44000000"))
            } else {
                Utils.doWavesLottie(visual, false)
                play_cover.clearColorFilter()
            }
        }
        play.setOnLongClickListener { v: View? ->
            MusicUtils.stop()
            true
        }
        Title = root.findViewById(R.id.mini_artist)
        Title.setOnClickListener { PlaceFactory.getPlayerPlace(mAccountId).tryOpenWith(context) }
        Title.isSelected = true
    }

    private fun TransformCover(): Transformation {
        return PolyTransformation()
    }

    @DrawableRes
    private val audioCoverSimple = R.drawable.audio_button_material

    private fun updatePlaybackControls() {
        if (Objects.nonNull(play_cover)) {
            if (MusicUtils.isPlaying()) {
                Utils.doWavesLottie(visual, true)
                play_cover.setColorFilter(Color.parseColor("#44000000"))
            } else {
                Utils.doWavesLottie(visual, false)
                play_cover.clearColorFilter()
            }
        }
    }

    private fun recvFullAudioInfo() {
        updateVisibility()
        updateNowPlayingInfo()
        updatePlaybackControls()
    }

    private fun onServiceBindEvent(@PlayerStatus status: Int) {
        when (status) {
            PlayerStatus.UPDATE_TRACK_INFO -> {
                updateVisibility()
                updateNowPlayingInfo()
                updatePlaybackControls()
            }
            PlayerStatus.UPDATE_PLAY_PAUSE -> {
                updateVisibility()
                updatePlaybackControls()
            }
            PlayerStatus.SERVICE_KILLED -> {
                updateVisibility()
                updatePlaybackControls()
                updateNowPlayingInfo()
            }
            PlayerStatus.REPEATMODE_CHANGED, PlayerStatus.SHUFFLEMODE_CHANGED -> {
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateNowPlayingInfo() {
        val artist = MusicUtils.getArtistName()
        val trackName = MusicUtils.getTrackName()
        Title.setTextWithAnimation(Utils.firstNonEmptyString(artist, " ") + " - " + Utils.firstNonEmptyString(trackName, " "), 350)
        if (Objects.nonNull(play_cover)) {
            val audio = MusicUtils.getCurrentAudio()
            val placeholder = AppCompatResources.getDrawable(context, audioCoverSimple)
            if (audio != null && placeholder != null && !Utils.isEmpty(audio.thumb_image_little)) {
                with()
                        .load(audio.thumb_image_little)
                        .placeholder(placeholder)
                        .transform(TransformCover())
                        .into(play_cover)
            } else {
                with().cancelRequest(play_cover)
                play_cover.setImageResource(audioCoverSimple)
            }
        }
        //queueNextRefresh(1);
    }

    private fun updateVisibility() {
        root.isVisible = MusicUtils.getMiniPlayerVisibility()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        recvFullAudioInfo()
        mAccountId = Settings.get()
                .accounts()
                .current
        mPlayerDisposable = MusicUtils.observeServiceBinding()
                .compose(RxUtils.applyObservableIOToMainSchedulers())
                .subscribe { status: Int -> onServiceBindEvent(status) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mPlayerDisposable.dispose()
    }
}