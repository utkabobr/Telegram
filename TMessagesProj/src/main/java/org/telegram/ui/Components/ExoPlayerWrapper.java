package org.telegram.ui.Components;

import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DeviceInfo;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AuxEffectInfo;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.video.VideoFrameMetadataListener;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.exoplayer2.video.spherical.CameraMotionListener;

import org.telegram.messenger.DispatchQueue;

import java.util.List;

public class ExoPlayerWrapper implements ExoPlayer {
    private Player player;

    public ExoPlayerWrapper(Player player) {
        this.player = player;
    }

    public void swapTo(Player player) {
        this.player = player;
    }

    public boolean isExoPlayer() {
        return player instanceof ExoPlayer;
    }

    @Override
    public Looper getApplicationLooper() {
        return player.getApplicationLooper();
    }

    @Override
    public void addListener(Listener listener) {
        player.addListener(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        player.removeListener(listener);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems) {
        player.setMediaItems(mediaItems);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
        player.setMediaItems(mediaItems, resetPosition);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        player.setMediaItems(mediaItems, startIndex, startPositionMs);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem) {
        player.setMediaItem(mediaItem);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
        player.setMediaItem(mediaItem, startPositionMs);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
        player.setMediaItem(mediaItem, resetPosition);
    }

    @Override
    public void addMediaItem(MediaItem mediaItem) {
        player.addMediaItem(mediaItem);
    }

    @Override
    public void addMediaItem(int index, MediaItem mediaItem) {
        player.addMediaItem(index, mediaItem);
    }

    @Override
    public void addMediaItems(List<MediaItem> mediaItems) {
        player.addMediaItems(mediaItems);
    }

    @Override
    public void addMediaItems(int index, List<MediaItem> mediaItems) {
        player.addMediaItems(index, mediaItems);
    }

    @Override
    public void moveMediaItem(int currentIndex, int newIndex) {
        player.moveMediaItem(currentIndex, newIndex);
    }

    @Override
    public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
        player.moveMediaItems(fromIndex, toIndex, newIndex);
    }

    @Override
    public void removeMediaItem(int index) {
        player.removeMediaItem(index);
    }

    @Override
    public void removeMediaItems(int fromIndex, int toIndex) {
        player.removeMediaItems(fromIndex, toIndex);
    }

    @Override
    public void clearMediaItems() {
        player.clearMediaItems();
    }

    @Override
    public boolean isCommandAvailable(int command) {
        return player.isCommandAvailable(command);
    }

    @Override
    public boolean canAdvertiseSession() {
        return player.canAdvertiseSession();
    }

    @Override
    public Commands getAvailableCommands() {
        return player.getAvailableCommands();
    }

    @Override
    public void prepare() {
        player.prepare();
    }

    @Override
    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    @Override
    public int getPlaybackSuppressionReason() {
        return player.getPlaybackSuppressionReason();
    }

    @Override
    public boolean isPlaying() {
        return player.isPlaying();
    }

    @Override
    public void play() {
        player.play();
    }

    @Override
    public void pause() {
        player.pause();
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        player.setPlayWhenReady(playWhenReady);
    }

    @Override
    public boolean getPlayWhenReady() {
        return player.getPlayWhenReady();
    }

    @Override
    public void setRepeatMode(int repeatMode) {
        player.setRepeatMode(repeatMode);
    }

    @Override
    public int getRepeatMode() {
        return player.getRepeatMode();
    }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
        player.setShuffleModeEnabled(shuffleModeEnabled);
    }

    @Override
    public boolean getShuffleModeEnabled() {
        return player.getShuffleModeEnabled();
    }

    @Override
    public boolean isLoading() {
        return player.isLoading();
    }

    @Override
    public void seekToDefaultPosition() {
        player.seekToDefaultPosition();
    }

    @Override
    public void seekToDefaultPosition(int mediaItemIndex) {
        player.seekToDefaultPosition(mediaItemIndex);
    }

    @Override
    public void seekTo(long positionMs) {
        player.seekTo(positionMs);
    }

    @Override
    public void seekTo(int mediaItemIndex, long positionMs) {
        player.seekTo(mediaItemIndex, positionMs);
    }

    @Override
    public long getSeekBackIncrement() {
        return player.getSeekBackIncrement();
    }

    @Override
    public void seekBack() {
        player.seekBack();
    }

    @Override
    public long getSeekForwardIncrement() {
        return player.getSeekForwardIncrement();
    }

    @Override
    public void seekForward() {
        player.seekForward();
    }

    @Override
    public boolean hasPrevious() {
        return player.hasPrevious();
    }

    @Override
    public boolean hasPreviousWindow() {
        return player.hasPreviousWindow();
    }

    @Override
    public boolean hasPreviousMediaItem() {
        return player.hasPreviousMediaItem();
    }

    @Override
    public void previous() {
        player.previous();
    }

    @Override
    public void seekToPreviousWindow() {
        player.seekToPreviousWindow();
    }

    @Override
    public void seekToPreviousMediaItem() {
        player.seekToPreviousMediaItem();
    }

    @Override
    public long getMaxSeekToPreviousPosition() {
        return player.getMaxSeekToPreviousPosition();
    }

    @Override
    public void seekToPrevious() {
        player.seekToPrevious();
    }

    @Override
    public boolean hasNext() {
        return player.hasNext();
    }

    @Override
    public boolean hasNextWindow() {
        return player.hasNextWindow();
    }

    @Override
    public boolean hasNextMediaItem() {
        return player.hasNextMediaItem();
    }

    @Override
    public void next() {
        player.next();
    }

    @Override
    public void seekToNextWindow() {
        player.seekToNextWindow();
    }

    @Override
    public void seekToNextMediaItem() {
        player.seekToNextMediaItem();
    }

    @Override
    public void seekToNext() {
        player.seekToNext();
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
        player.setPlaybackParameters(playbackParameters);
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        player.setPlaybackSpeed(speed);
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        return player.getPlaybackParameters();
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public void stop(boolean reset) {
        player.stop(reset);
    }

    @Override
    public void release() {
        player.release();
    }

    @Override
    public Tracks getCurrentTracks() {
        return player.getCurrentTracks();
    }

    @Override
    public TrackSelectionParameters getTrackSelectionParameters() {
        return player.getTrackSelectionParameters();
    }

    @Override
    public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
        player.setTrackSelectionParameters(parameters);
    }

    @Override
    public MediaMetadata getMediaMetadata() {
        return player.getMediaMetadata();
    }

    @Override
    public MediaMetadata getPlaylistMetadata() {
        return player.getPlaylistMetadata();
    }

    @Override
    public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
        player.setPlaylistMetadata(mediaMetadata);
    }

    @Nullable
    @Override
    public Object getCurrentManifest() {
        return player.getCurrentManifest();
    }

    @Override
    public Timeline getCurrentTimeline() {
        return player.getCurrentTimeline();
    }

    @Override
    public int getCurrentPeriodIndex() {
        return player.getCurrentPeriodIndex();
    }

    @Override
    public int getCurrentWindowIndex() {
        return player.getCurrentWindowIndex();
    }

    @Override
    public int getCurrentMediaItemIndex() {
        return player.getCurrentMediaItemIndex();
    }

    @Override
    public int getNextWindowIndex() {
        return player.getNextWindowIndex();
    }

    @Override
    public int getNextMediaItemIndex() {
        return player.getNextMediaItemIndex();
    }

    @Override
    public int getPreviousWindowIndex() {
        return player.getPreviousWindowIndex();
    }

    @Override
    public int getPreviousMediaItemIndex() {
        return player.getPreviousMediaItemIndex();
    }

    @Nullable
    @Override
    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    @Override
    public int getMediaItemCount() {
        return player.getMediaItemCount();
    }

    @Override
    public MediaItem getMediaItemAt(int index) {
        return player.getMediaItemAt(index);
    }

    @Override
    public long getDuration() {
        return player.getDuration();
    }

    @Override
    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    @Override
    public long getBufferedPosition() {
        return player.getBufferedPosition();
    }

    @Override
    public int getBufferedPercentage() {
        return player.getBufferedPercentage();
    }

    @Override
    public long getTotalBufferedDuration() {
        return player.getTotalBufferedDuration();
    }

    @Override
    public boolean isCurrentWindowDynamic() {
        return player.isCurrentWindowDynamic();
    }

    @Override
    public boolean isCurrentMediaItemDynamic() {
        return player.isCurrentMediaItemDynamic();
    }

    @Override
    public boolean isCurrentWindowLive() {
        return player.isCurrentWindowLive();
    }

    @Override
    public boolean isCurrentMediaItemLive() {
        return player.isCurrentMediaItemLive();
    }

    @Override
    public long getCurrentLiveOffset() {
        return player.getCurrentLiveOffset();
    }

    @Override
    public boolean isCurrentWindowSeekable() {
        return player.isCurrentWindowSeekable();
    }

    @Override
    public boolean isCurrentMediaItemSeekable() {
        return player.isCurrentMediaItemSeekable();
    }

    @Override
    public boolean isPlayingAd() {
        return player.isPlayingAd();
    }

    @Override
    public int getCurrentAdGroupIndex() {
        return player.getCurrentAdGroupIndex();
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
        return player.getCurrentAdIndexInAdGroup();
    }

    @Override
    public long getContentDuration() {
        return player.getContentDuration();
    }

    @Override
    public long getContentPosition() {
        return player.getContentPosition();
    }

    @Override
    public long getContentBufferedPosition() {
        return player.getContentBufferedPosition();
    }

    @Override
    public AudioAttributes getAudioAttributes() {
        return player.getAudioAttributes();
    }

    @Override
    public void setVolume(float volume) {
        player.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return player.getVolume();
    }

    @Override
    public void clearVideoSurface() {
        player.clearVideoSurface();
    }

    @Override
    public void clearVideoSurface(@Nullable Surface surface) {
        player.clearVideoSurface(surface);
    }

    @Override
    public void setVideoSurface(@Nullable Surface surface) {
        player.setVideoSurface(surface);
    }

    @Override
    public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
        player.setVideoSurfaceHolder(surfaceHolder);
    }

    @Override
    public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
        player.clearVideoSurfaceHolder(surfaceHolder);
    }

    @Override
    public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
        player.setVideoSurfaceView(surfaceView);
    }

    @Override
    public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
        player.clearVideoSurfaceView(surfaceView);
    }

    @Override
    public void setVideoTextureView(@Nullable TextureView textureView) {
        player.setVideoTextureView(textureView);
    }

    @Override
    public void clearVideoTextureView(@Nullable TextureView textureView) {
        player.clearVideoTextureView(textureView);
    }

    @Override
    public VideoSize getVideoSize() {
        return player.getVideoSize();
    }

    @Override
    public Size getSurfaceSize() {
        return player.getSurfaceSize();
    }

    @Override
    public CueGroup getCurrentCues() {
        return player.getCurrentCues();
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return player.getDeviceInfo();
    }

    @Override
    public int getDeviceVolume() {
        return player.getDeviceVolume();
    }

    @Override
    public boolean isDeviceMuted() {
        return player.isDeviceMuted();
    }

    @Override
    public void setDeviceVolume(int volume) {
        player.setDeviceVolume(volume);
    }

    @Override
    public void increaseDeviceVolume() {
        player.increaseDeviceVolume();
    }

    @Override
    public void decreaseDeviceVolume() {
        player.decreaseDeviceVolume();
    }

    @Override
    public void setDeviceMuted(boolean muted) {
        player.setDeviceMuted(muted);
    }

    @Override
    public void addVideoListener(VideoListener listener) {
        player.addVideoListener(listener);
    }

    @Override
    public void removeVideoListener(VideoListener listener) {
        player.removeVideoListener(listener);
    }

    @Override
    public void setWorkerQueue(DispatchQueue dispatchQueue) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setWorkerQueue(dispatchQueue);
        }
    }

    @Nullable
    @Override
    public ExoPlaybackException getPlayerError() {
        return (ExoPlaybackException) player.getPlayerError();
    }

    @Nullable
    @Override
    public AudioComponent getAudioComponent() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getAudioComponent();
        }
        return null;
    }

    @Nullable
    @Override
    public VideoComponent getVideoComponent() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getVideoComponent();
        }
        return null;
    }

    @Nullable
    @Override
    public TextComponent getTextComponent() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getTextComponent();
        }
        return null;
    }

    @Nullable
    @Override
    public DeviceComponent getDeviceComponent() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getDeviceComponent();
        }
        return null;
    }

    @Override
    public void addAudioOffloadListener(AudioOffloadListener listener) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).addAudioOffloadListener(listener);
        }
    }

    @Override
    public void removeAudioOffloadListener(AudioOffloadListener listener) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).removeAudioOffloadListener(listener);
        }
    }

    @Override
    public AnalyticsCollector getAnalyticsCollector() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getAnalyticsCollector();
        }
        return null;
    }

    @Override
    public void addAnalyticsListener(AnalyticsListener listener) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).addAnalyticsListener(listener);
        }
    }

    @Override
    public void removeAnalyticsListener(AnalyticsListener listener) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).removeAnalyticsListener(listener);
        }
    }

    @Override
    public int getRendererCount() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getRendererCount();
        }
        return 0;
    }

    @Override
    public @C.TrackType int getRendererType(int index) {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getRendererType(index);
        }
        return 0;
    }

    @Override
    public Renderer getRenderer(int index) {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getRenderer(index);
        }
        return null;
    }

    @Nullable
    @Override
    public TrackSelector getTrackSelector() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getTrackSelector();
        }
        return null;
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getCurrentTrackGroups();
        }
        return null;
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getCurrentTrackSelections();
        }
        return null;
    }

    @Override
    public Looper getPlaybackLooper() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getPlaybackLooper();
        }
        return null;
    }

    @Override
    public Clock getClock() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getClock();
        }
        return null;
    }

    @Override
    public void retry() {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).retry();
        }
    }

    @Override
    public void prepare(MediaSource mediaSource) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).prepare(mediaSource);
        }
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).prepare(mediaSource, resetPosition, resetState);
        }
    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setMediaSources(mediaSources);
        }
    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setMediaSources(mediaSources, resetPosition);
        }
    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources, int startMediaItemIndex, long startPositionMs) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setMediaSources(mediaSources, startMediaItemIndex, startPositionMs);
        }
    }

    @Override
    public void setMediaSource(MediaSource mediaSource) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setMediaSource(mediaSource);
        }
    }

    @Override
    public void setMediaSource(MediaSource mediaSource, long startPositionMs) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setMediaSource(mediaSource, startPositionMs);
        }
    }

    @Override
    public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setMediaSource(mediaSource, resetPosition);
        }
    }

    @Override
    public void addMediaSource(MediaSource mediaSource) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).addMediaSource(mediaSource);
        }
    }

    @Override
    public void addMediaSource(int index, MediaSource mediaSource) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).addMediaSource(index, mediaSource);
        }
    }

    @Override
    public void addMediaSources(List<MediaSource> mediaSources) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).addMediaSources(mediaSources);
        }
    }

    @Override
    public void addMediaSources(int index, List<MediaSource> mediaSources) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).addMediaSources(index, mediaSources);
        }
    }

    @Override
    public void setShuffleOrder(ShuffleOrder shuffleOrder) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setShuffleOrder(shuffleOrder);
        }
    }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setAudioAttributes(audioAttributes, handleAudioFocus);
        }
    }

    @Override
    public void setAudioSessionId(int audioSessionId) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setAudioSessionId(audioSessionId);
        }
    }

    @Override
    public int getAudioSessionId() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getAudioSessionId();
        }
        return 0;
    }

    @Override
    public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setAuxEffectInfo(auxEffectInfo);
        }
    }

    @Override
    public void clearAuxEffectInfo() {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).clearAuxEffectInfo();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void setPreferredAudioDevice(@Nullable AudioDeviceInfo audioDeviceInfo) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setPreferredAudioDevice(audioDeviceInfo);
        }
    }

    @Override
    public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setSkipSilenceEnabled(skipSilenceEnabled);
        }
    }

    @Override
    public boolean getSkipSilenceEnabled() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getSkipSilenceEnabled();
        }
        return false;
    }

    @Override
    public void setVideoScalingMode(int videoScalingMode) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setVideoScalingMode(videoScalingMode);
        }
    }

    @Override
    public int getVideoScalingMode() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getVideoScalingMode();
        }
        return C.VIDEO_SCALING_MODE_DEFAULT;
    }

    @Override
    public void setVideoChangeFrameRateStrategy(int videoChangeFrameRateStrategy) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy);
        }
    }

    @Override
    public int getVideoChangeFrameRateStrategy() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getVideoChangeFrameRateStrategy();
        }
        return C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF;
    }

    @Override
    public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setVideoFrameMetadataListener(listener);
        }
    }

    @Override
    public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).clearVideoFrameMetadataListener(listener);
        }
    }

    @Override
    public void setCameraMotionListener(CameraMotionListener listener) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setCameraMotionListener(listener);
        }
    }

    @Override
    public void clearCameraMotionListener(CameraMotionListener listener) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).clearCameraMotionListener(listener);
        }
    }

    @Override
    public PlayerMessage createMessage(PlayerMessage.Target target) {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).createMessage(target);
        }
        return null;
    }

    @Override
    public void setSeekParameters(@Nullable SeekParameters seekParameters) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setSeekParameters(seekParameters);
        }
    }

    @Override
    public SeekParameters getSeekParameters() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getSeekParameters();
        }
        return null;
    }

    @Override
    public void setForegroundMode(boolean foregroundMode) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setForegroundMode(foregroundMode);
        }
    }

    @Override
    public void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems);
        }
    }

    @Override
    public boolean getPauseAtEndOfMediaItems() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getPauseAtEndOfMediaItems();
        }
        return false;
    }

    @Nullable
    @Override
    public Format getAudioFormat() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getAudioFormat();
        }
        return null;
    }

    @Nullable
    @Override
    public Format getVideoFormat() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getVideoFormat();
        }
        return null;
    }

    @Nullable
    @Override
    public DecoderCounters getAudioDecoderCounters() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getAudioDecoderCounters();
        }
        return null;
    }

    @Nullable
    @Override
    public DecoderCounters getVideoDecoderCounters() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).getVideoDecoderCounters();
        }
        return null;
    }

    @Override
    public void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setHandleAudioBecomingNoisy(handleAudioBecomingNoisy);
        }
    }

    @Override
    public void setHandleWakeLock(boolean handleWakeLock) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setHandleWakeLock(handleWakeLock);
        }
    }

    @Override
    public void setWakeMode(int wakeMode) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setWakeMode(wakeMode);
        }
    }

    @Override
    public void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).setPriorityTaskManager(priorityTaskManager);
        }
    }

    @Override
    public void experimentalSetOffloadSchedulingEnabled(boolean offloadSchedulingEnabled) {
        if (player instanceof ExoPlayer) {
            ((ExoPlayer) player).experimentalSetOffloadSchedulingEnabled(offloadSchedulingEnabled);
        }
    }

    @Override
    public boolean experimentalIsSleepingForOffload() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).experimentalIsSleepingForOffload();
        }
        return false;
    }

    @Override
    public boolean isTunnelingEnabled() {
        if (player instanceof ExoPlayer) {
            return ((ExoPlayer) player).isTunnelingEnabled();
        }
        return false;
    }
}
