package com.identitycrisis.client.audio;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.net.URL;

/**
 * BGM and SFX via JavaFX MediaPlayer/AudioClip.
 */
public class AudioManager {

    private MediaPlayer bgmPlayer;
    private double volume = 0.5;
    private String currentBgmPath;
    private boolean muted = false;

    public AudioManager() { }

    /**
     * Plays the BGM from the given resource path. 
     * If the same BGM is already playing, it does nothing.
     */
    public void playBGM(String resourcePath) {
        if (resourcePath.equals(currentBgmPath) && bgmPlayer != null && bgmPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            return;
        }

        stopBGM();

        try {
            URL resource = getClass().getResource(resourcePath);
            if (resource == null) {
                System.err.println("Could not find audio resource: " + resourcePath);
                return;
            }
            Media media = new Media(resource.toExternalForm());
            bgmPlayer = new MediaPlayer(media);
            bgmPlayer.setVolume(volume);
            bgmPlayer.setMute(muted);
            bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            bgmPlayer.play();
            currentBgmPath = resourcePath;
        } catch (Exception e) {
            System.err.println("Error playing BGM: " + e.getMessage());
        }
    }

    public void stopBGM() {
        if (bgmPlayer != null) {
            bgmPlayer.stop();
            bgmPlayer = null;
            currentBgmPath = null;
        }
    }

    public void toggleMute() {
        muted = !muted;
        if (bgmPlayer != null) {
            bgmPlayer.setMute(muted);
        }
    }

    public boolean isMuted() {
        return muted;
    }

    public void playSFX(String sfxName) {
        // TODO: Implement SFX via AudioClip
    }

    public void setVolume(double volume) {
        this.volume = volume;
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(volume);
        }
    }
}
