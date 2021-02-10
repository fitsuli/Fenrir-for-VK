package dev.ragnarok.fenrir.adapter;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.squareup.picasso.Transformation;
import com.umerov.rlottie.RLottieImageView;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import dev.ragnarok.fenrir.Constants;
import dev.ragnarok.fenrir.R;
import dev.ragnarok.fenrir.domain.ILocalServerInteractor;
import dev.ragnarok.fenrir.domain.InteractorFactory;
import dev.ragnarok.fenrir.link.VkLinkParser;
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.ModalBottomSheetDialogFragment;
import dev.ragnarok.fenrir.modalbottomsheetdialogfragment.OptionRequest;
import dev.ragnarok.fenrir.model.Audio;
import dev.ragnarok.fenrir.model.menu.AudioItem;
import dev.ragnarok.fenrir.picasso.PicassoInstance;
import dev.ragnarok.fenrir.picasso.transforms.RoundTransformation;
import dev.ragnarok.fenrir.place.PlaceFactory;
import dev.ragnarok.fenrir.player.util.MusicUtils;
import dev.ragnarok.fenrir.settings.CurrentTheme;
import dev.ragnarok.fenrir.settings.Settings;
import dev.ragnarok.fenrir.util.AppPerms;
import dev.ragnarok.fenrir.util.CustomToast;
import dev.ragnarok.fenrir.util.DownloadWorkUtils;
import dev.ragnarok.fenrir.util.RxUtils;
import dev.ragnarok.fenrir.util.Utils;
import dev.ragnarok.fenrir.view.WeakViewAnimatorAdapter;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

import static dev.ragnarok.fenrir.player.util.MusicUtils.observeServiceBinding;
import static dev.ragnarok.fenrir.util.Utils.firstNonEmptyString;

public class AudioLocalServerRecyclerAdapter extends RecyclerView.Adapter<AudioLocalServerRecyclerAdapter.AudioHolder> {

    private final Context mContext;
    private final ILocalServerInteractor mAudioInteractor;
    private ClickListener mClickListener;
    private Disposable mPlayerDisposable = Disposable.disposed();
    private Disposable audioListDisposable = Disposable.disposed();
    private List<Audio> data;
    private Audio currAudio;

    public AudioLocalServerRecyclerAdapter(Context context, List<Audio> data) {
        this.data = data;
        mContext = context;
        currAudio = MusicUtils.getCurrentAudio();
        mAudioInteractor = InteractorFactory.createLocalServerInteractor();
    }

    private static String BytesToSize(long Bytes) {
        long tb = 1099511627776L;
        long gb = 1073741824;
        long mb = 1048576;
        long kb = 1024;

        String returnSize;
        if (Bytes >= tb)
            returnSize = String.format(Locale.getDefault(), "%.2f TB", (double) Bytes / tb);
        else if (Bytes >= gb)
            returnSize = String.format(Locale.getDefault(), "%.2f GB", (double) Bytes / gb);
        else if (Bytes >= mb)
            returnSize = String.format(Locale.getDefault(), "%.2f MB", (double) Bytes / mb);
        else if (Bytes >= kb)
            returnSize = String.format(Locale.getDefault(), "%.2f KB", (double) Bytes / kb);
        else returnSize = String.format(Locale.getDefault(), "%d Bytes", Bytes);
        return returnSize;
    }

    public void setItems(List<Audio> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    private Single<Long> doLocalBitrate(String url) {
        try {
            Cursor cursor = mContext.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.MediaColumns.DATA},
                    BaseColumns._ID + "=? ",
                    new String[]{Uri.parse(url).getLastPathSegment()}, null);
            if (cursor != null && cursor.moveToFirst()) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                String fl = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA));
                retriever.setDataSource(fl);
                cursor.close();
                String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                if (bitrate != null) {
                    return Single.just(Long.parseLong(bitrate) / 1000);
                }
                return Single.error(new Throwable("Can't receipt bitrate "));
            }
            return Single.error(new Throwable("Can't receipt bitrate "));
        } catch (RuntimeException e) {
            return Single.error(e);
        }
    }

    private void getLocalBitrate(String url) {
        if (Utils.isEmpty(url)) {
            return;
        }
        audioListDisposable = doLocalBitrate(url).compose(RxUtils.applySingleIOToMainSchedulers())
                .subscribe(r -> CustomToast.CreateCustomToast(mContext).showToast(mContext.getResources().getString(R.string.bitrate) + " " + r + " bit"),
                        e -> Utils.showErrorInAdapter((Activity) mContext, e));
    }

    @DrawableRes
    private int getAudioCoverSimple() {
        return R.drawable.audio_button;
    }

    private Transformation TransformCover() {
        return new RoundTransformation();
    }

    private void updateAudioStatus(AudioHolder holder, Audio audio) {
        if (!audio.equals(currAudio)) {
            holder.visual.setImageResource(Utils.isEmpty(audio.getUrl()) ? R.drawable.audio_died : R.drawable.song);
            holder.play_cover.clearColorFilter();
            return;
        }
        switch (MusicUtils.PlayerStatus()) {
            case 1:
                Utils.doWavesLottie(holder.visual, true);
                holder.play_cover.setColorFilter(Color.parseColor("#44000000"));
                break;
            case 2:
                Utils.doWavesLottie(holder.visual, false);
                holder.play_cover.setColorFilter(Color.parseColor("#44000000"));
                break;

        }
    }

    @NotNull
    @Override
    public AudioHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        return new AudioHolder(LayoutInflater.from(mContext).inflate(R.layout.item_audio_local_server, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AudioHolder holder, int position) {
        Audio audio = data.get(position);

        holder.cancelSelectionAnimation();
        if (audio.isAnimationNow()) {
            holder.startSelectionAnimation();
            audio.setAnimationNow(false);
        }

        holder.artist.setText(audio.getArtist());
        holder.title.setText(audio.getTitle());

        if (audio.getDuration() <= 0)
            holder.time.setVisibility(View.INVISIBLE);
        else {
            holder.time.setVisibility(View.VISIBLE);
            holder.time.setText(BytesToSize(audio.getDuration()));
        }

        int Status = DownloadWorkUtils.TrackIsDownloaded(audio);
        if (Status == 2) {
            holder.saved.setImageResource(R.drawable.remote_cloud);
        } else {
            holder.saved.setImageResource(R.drawable.save);
        }
        holder.saved.setVisibility(Status != 0 ? View.VISIBLE : View.GONE);

        updateAudioStatus(holder, audio);

        if (Settings.get().other().isShow_audio_cover()) {
            if (!Utils.isEmpty(audio.getThumb_image_little())) {
                PicassoInstance.with()
                        .load(audio.getThumb_image_little())
                        .placeholder(Objects.requireNonNull(ResourcesCompat.getDrawable(mContext.getResources(), getAudioCoverSimple(), mContext.getTheme())))
                        .transform(TransformCover())
                        .tag(Constants.PICASSO_TAG)
                        .into(holder.play_cover);
            } else {
                PicassoInstance.with().cancelRequest(holder.play_cover);
                holder.play_cover.setImageResource(getAudioCoverSimple());
            }
        } else {
            PicassoInstance.with().cancelRequest(holder.play_cover);
            holder.play_cover.setImageResource(getAudioCoverSimple());
        }

        holder.play.setOnLongClickListener(v -> {
            if ((!Utils.isEmpty(audio.getThumb_image_very_big())
                    || !Utils.isEmpty(audio.getThumb_image_big()) || !Utils.isEmpty(audio.getThumb_image_little())) && !Utils.isEmpty(audio.getArtist()) && !Utils.isEmpty(audio.getTitle())) {
                mClickListener.onUrlPhotoOpen(firstNonEmptyString(audio.getThumb_image_very_big(),
                        audio.getThumb_image_big(), audio.getThumb_image_little()), audio.getArtist(), audio.getTitle());
            }
            return true;
        });

        holder.play.setOnClickListener(v -> {
            if (MusicUtils.isNowPlayingOrPreparingOrPaused(audio)) {
                if (!Settings.get().other().isUse_stop_audio()) {
                    MusicUtils.playOrPause();
                } else {
                    MusicUtils.stop();
                }
            } else {
                if (mClickListener != null) {
                    mClickListener.onClick(position, audio);
                }
            }
        });

        holder.Track.setOnLongClickListener(v -> {
            if (!AppPerms.hasReadWriteStoragePermission(mContext)) {
                if (mClickListener != null) {
                    mClickListener.onRequestWritePermissions();
                }
                return false;
            }
            holder.saved.setVisibility(View.VISIBLE);
            holder.saved.setImageResource(R.drawable.save);
            int ret = DownloadWorkUtils.doDownloadAudio(mContext, audio, Settings.get().accounts().getCurrent(), false);
            if (ret == 0)
                CustomToast.CreateCustomToast(mContext).showToastBottom(R.string.saved_audio);
            else if (ret == 1 || ret == 2) {
                Utils.ThemedSnack(v, ret == 1 ? R.string.audio_force_download : R.string.audio_force_download_pc, BaseTransientBottomBar.LENGTH_LONG).setAction(R.string.button_yes,
                        v1 -> DownloadWorkUtils.doDownloadAudio(mContext, audio, Settings.get().accounts().getCurrent(), true)).show();
            } else {
                holder.saved.setVisibility(View.GONE);
                CustomToast.CreateCustomToast(mContext).showToastBottom(R.string.error_audio);
            }
            return true;
        });

        holder.Track.setOnClickListener(view -> {
            holder.cancelSelectionAnimation();
            holder.startSomeAnimation();

            ModalBottomSheetDialogFragment.Builder menus = new ModalBottomSheetDialogFragment.Builder();

            menus.add(new OptionRequest(AudioItem.save_item_audio, mContext.getString(R.string.download), R.drawable.save));
            menus.add(new OptionRequest(AudioItem.play_item_audio, mContext.getString(R.string.play), R.drawable.play));
            menus.add(new OptionRequest(AudioItem.bitrate_item_audio, mContext.getString(R.string.get_bitrate), R.drawable.high_quality));
            menus.add(new OptionRequest(AudioItem.edit_track, mContext.getString(R.string.update_time), R.drawable.ic_recent));


            menus.header(firstNonEmptyString(audio.getArtist(), " ") + " - " + audio.getTitle(), R.drawable.song, audio.getThumb_image_little());
            menus.columns(2);
            menus.show(((FragmentActivity) mContext).getSupportFragmentManager(), "audio_options", option -> {
                switch (option.getId()) {
                    case AudioItem.save_item_audio:
                        if (!AppPerms.hasReadWriteStoragePermission(mContext)) {
                            if (mClickListener != null) {
                                mClickListener.onRequestWritePermissions();
                            }
                            break;
                        }
                        holder.saved.setVisibility(View.VISIBLE);
                        holder.saved.setImageResource(R.drawable.save);
                        int ret = DownloadWorkUtils.doDownloadAudio(mContext, audio, Settings.get().accounts().getCurrent(), false);
                        if (ret == 0)
                            CustomToast.CreateCustomToast(mContext).showToastBottom(R.string.saved_audio);
                        else if (ret == 1 || ret == 2) {
                            Utils.ThemedSnack(view, ret == 1 ? R.string.audio_force_download : R.string.audio_force_download_pc, BaseTransientBottomBar.LENGTH_LONG).setAction(R.string.button_yes,
                                    v1 -> DownloadWorkUtils.doDownloadAudio(mContext, audio, Settings.get().accounts().getCurrent(), true)).show();
                        } else {
                            holder.saved.setVisibility(View.GONE);
                            CustomToast.CreateCustomToast(mContext).showToastBottom(R.string.error_audio);
                        }
                        break;
                    case AudioItem.play_item_audio:
                        if (mClickListener != null) {
                            mClickListener.onClick(position, audio);
                            if (Settings.get().other().isShow_mini_player())
                                PlaceFactory.getPlayerPlace(Settings.get().accounts().getCurrent()).tryOpenWith(mContext);
                        }
                        break;
                    case AudioItem.bitrate_item_audio:
                        getLocalBitrate(audio.getUrl());
                        break;
                    case AudioItem.edit_track:
                        String hash = VkLinkParser.parseLocalServerURL(audio.getUrl());
                        if (Utils.isEmpty(hash)) {
                            break;
                        }
                        audioListDisposable = mAudioInteractor.update_time(hash).compose(RxUtils.applySingleIOToMainSchedulers()).subscribe(t -> CustomToast.CreateCustomToast(mContext).showToast(R.string.success), t -> Utils.showErrorInAdapter((Activity) mContext, t));
                        break;
                    default:
                        break;
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public void onAttachedToRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mPlayerDisposable = observeServiceBinding()
                .compose(RxUtils.applyObservableIOToMainSchedulers())
                .subscribe(this::onServiceBindEvent);
    }

    @Override
    public void onDetachedFromRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        mPlayerDisposable.dispose();
        audioListDisposable.dispose();
    }

    private void onServiceBindEvent(@MusicUtils.PlayerStatus int status) {
        switch (status) {
            case MusicUtils.PlayerStatus.UPDATE_TRACK_INFO:
            case MusicUtils.PlayerStatus.SERVICE_KILLED:
            case MusicUtils.PlayerStatus.UPDATE_PLAY_PAUSE:
                updateAudio(currAudio);
                currAudio = MusicUtils.getCurrentAudio();
                updateAudio(currAudio);
                break;
            case MusicUtils.PlayerStatus.REPEATMODE_CHANGED:
            case MusicUtils.PlayerStatus.SHUFFLEMODE_CHANGED:
                break;
        }
    }

    private void updateAudio(Audio audio) {
        int pos = data.indexOf(audio);
        if (pos != -1) {
            notifyItemChanged(pos);
        }
    }

    public void setClickListener(ClickListener clickListener) {
        mClickListener = clickListener;
    }

    public interface ClickListener {
        void onClick(int position, Audio audio);

        void onUrlPhotoOpen(@NonNull String url, @NonNull String prefix, @NonNull String photo_prefix);

        void onRequestWritePermissions();
    }

    class AudioHolder extends RecyclerView.ViewHolder {

        final TextView artist;
        final TextView title;
        final View play;
        final ImageView play_cover;
        final View Track;
        final ImageView saved;
        final MaterialCardView selectionView;
        final Animator.AnimatorListener animationAdapter;
        final RLottieImageView visual;
        final TextView time;
        ObjectAnimator animator;

        AudioHolder(View itemView) {
            super(itemView);
            artist = itemView.findViewById(R.id.dialog_title);
            title = itemView.findViewById(R.id.dialog_message);
            time = itemView.findViewById(R.id.item_audio_time);
            play = itemView.findViewById(R.id.item_audio_play);
            saved = itemView.findViewById(R.id.saved);
            play_cover = itemView.findViewById(R.id.item_audio_play_cover);
            Track = itemView.findViewById(R.id.track_option);
            selectionView = itemView.findViewById(R.id.item_audio_selection);
            visual = itemView.findViewById(R.id.item_audio_visual);
            animationAdapter = new WeakViewAnimatorAdapter<View>(selectionView) {
                @Override
                public void onAnimationEnd(View view) {
                    view.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationStart(View view) {
                    view.setVisibility(View.VISIBLE);
                }

                @Override
                protected void onAnimationCancel(View view) {
                    view.setVisibility(View.GONE);
                }
            };
        }

        void startSelectionAnimation() {
            selectionView.setCardBackgroundColor(CurrentTheme.getColorPrimary(mContext));
            selectionView.setAlpha(0.5f);

            animator = ObjectAnimator.ofFloat(selectionView, View.ALPHA, 0.0f);
            animator.setDuration(1500);
            animator.addListener(animationAdapter);
            animator.start();
        }

        void startSomeAnimation() {
            selectionView.setCardBackgroundColor(CurrentTheme.getColorSecondary(mContext));
            selectionView.setAlpha(0.5f);

            animator = ObjectAnimator.ofFloat(selectionView, View.ALPHA, 0.0f);
            animator.setDuration(500);
            animator.addListener(animationAdapter);
            animator.start();
        }

        void cancelSelectionAnimation() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }

            selectionView.setVisibility(View.INVISIBLE);
        }
    }
}
