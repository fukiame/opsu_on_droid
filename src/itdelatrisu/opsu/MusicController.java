/*
 * opsu! - an open-source osu! client
 * Copyright (C) 2014 Jeffrey Han
 *
 * opsu! is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * opsu! is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with opsu!.  If not, see <http://www.gnu.org/licenses/>.
 */

package itdelatrisu.opsu;

import itdelatrisu.opsu.states.Options;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.util.concurrent.TimeUnit;

import javazoom.jl.converter.Converter;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.newdawn.slick.Music;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.openal.Audio;
import org.newdawn.slick.openal.SoundStore;
import org.newdawn.slick.util.Log;

/**
 * Controller for all music.
 */
public class MusicController {
	/**
	 * The current music track.
	 */
	private static Music player;

	/**
	 * The last OsuFile passed to play().
	 */
	private static OsuFile lastOsu;

	/**
	 * Temporary WAV file for file conversions (to be deleted).
	 */
	private static File wavFile;

	/**
	 * Thread for MP3 conversions.
	 */
	private static Thread conversion;

	// This class should not be instantiated.
	private MusicController() {}

	/**
	 * Plays an audio file at the preview position.
	 */
	@SuppressWarnings("deprecation")
	public static void play(final OsuFile osu, final boolean loop) {
		if (lastOsu == null || !osu.audioFilename.equals(lastOsu.audioFilename)) {
			lastOsu = osu;

			// TODO: properly interrupt instead of using deprecated conversion.stop();
			// interrupt the conversion
			if (isConverting())
//				conversion.interrupt();
				conversion.stop();

			if (wavFile != null)
				wavFile.delete();

			// releases all sources from previous tracks
			destroyOpenAL();

			switch (OsuParser.getExtension(osu.audioFilename.getName())) {
			case "ogg":
				loadTrack(osu.audioFilename, osu.previewTime, loop);
				break;
			case "mp3":
				// convert MP3 to WAV in a new conversion
				conversion = new Thread() {
					@Override
					public void run() {
						convertMp3(osu.audioFilename);
//						if (!Thread.currentThread().isInterrupted())
							loadTrack(wavFile, osu.previewTime, loop);
					}
				};
				conversion.start();
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Loads a track and plays it.
	 */
	private static void loadTrack(File file, int previewTime, boolean loop) {
		try {   // create a new player
			player = new Music(file.getPath());
			playAt((previewTime > 0) ? previewTime : 0, loop);
		} catch (Exception e) {
			Log.error(String.format("Could not play track '%s'.", file.getName()), e);
		}
	}

	/**
	 * Plays the current track at the given position.
	 */
	public static void playAt(final int position, final boolean loop) {
		if (trackExists()) {
			SoundStore.get().setMusicVolume(Options.getMusicVolume());
			player.setPosition(position / 1000f);
			if (loop)
				player.loop();
			else
				player.play();
		}
	}

	/**
	 * Converts an MP3 file to a temporary WAV file.
	 */
	private static File convertMp3(File file) {
        try {
			wavFile = File.createTempFile(".osu", ".wav", Options.TMP_DIR);
			wavFile.deleteOnExit();

			Converter converter = new Converter();
			converter.convert(file.getPath(), wavFile.getPath());
			return wavFile;
        } catch (Exception e) {
			Log.error(String.format("Failed to play file '%s'.", file.getAbsolutePath()), e);
		}
		return wavFile;
	}

	/**
	 * Returns true if a conversion is running.
	 */
	public static boolean isConverting() {
		return (conversion != null && conversion.isAlive());
	}

	/**
	 * Returns true if a track is loaded.
	 */
	public static boolean trackExists() {
		return (player != null);
	}

	/**
	 * Returns the name of the current track.
	 */
	public static String getTrackName() {
		if (!trackExists() || lastOsu == null)
			return null;
		return lastOsu.title;
	}

	/**
	 * Returns the artist of the current track.
	 */
	public static String getArtistName() {
		if (!trackExists() || lastOsu == null)
			return null;
		return lastOsu.artist;
	}

	/**
	 * Returns true if the current track is playing.
	 */
	public static boolean isPlaying() {
		return (trackExists() && player.playing());
	}

	/**
	 * Pauses the current track.
	 */
	public static void pause() {
		if (isPlaying())
			player.pause();
	}

	/**
	 * Resumes the current track.
	 */
	public static void resume() {
		if (trackExists()) {
			player.resume();
			player.setVolume(1.0f);
		}
	}

	/**
	 * Stops the current track.
	 */
	public static void stop() {
		if (isPlaying())
			player.stop();
	}

	/**
	 * Fades out the track.
	 */
	public static void fadeOut(int duration) {
		if (isPlaying())
			player.fade(duration, 0f, true);
	}

	/**
	 * Returns the position in the current track.
	 * If no track is playing, 0f will be returned.
	 */
	public static int getPosition() {
		if (isPlaying())
			return Math.max((int) (player.getPosition() * 1000 + Options.getMusicOffset()), 0);
		else
			return 0;
	}

	/**
	 * Seeks to a position in the current track.
	 */
	public static boolean setPosition(int position) {
		return (trackExists() && player.setPosition(position / 1000f));
	}

	 /**
	 * Gets the length of the track, in milliseconds.
	 * Returns 0f if no file is loaded or an MP3 is currently being converted.
	 * @author bdk (http://slick.ninjacave.com/forum/viewtopic.php?t=2699)
	 */
	public static int getTrackLength() {
		if (!trackExists() || isConverting())
			return 0;

		float duration = 0f;
		try {
			// get Music object's (private) Audio object reference
			Field sound = player.getClass().getDeclaredField("sound");
			sound.setAccessible(true);
			Audio audio = (Audio) (sound.get(player));

			// access Audio object's (private)'length' field
			Field length = audio.getClass().getDeclaredField("length");
			length.setAccessible(true);
			duration = (float) (length.get(audio));
		} catch (Exception e) {
			Log.error("Could not get track length.", e);
		}
		return (int) (duration * 1000);
	}

	/**
	 * Gets the length of the track as a formatted string (M:SS).
	 * Returns "--" if an MP3 is currently being converted.
	 */
	public static String getTrackLengthString() {
		if (isConverting())
			return "...";

		int duration = getTrackLength();
		return String.format("%d:%02d",
			TimeUnit.MILLISECONDS.toMinutes(duration),
		    TimeUnit.MILLISECONDS.toSeconds(duration) - 
		    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)));
	}

	/**
	 * Stops and releases all sources, clears each of the specified Audio
	 * buffers, destroys the OpenAL context, and resets SoundStore for future use.
	 * 
	 * Calling SoundStore.get().init() will re-initialize the OpenAL context
	 * after a call to destroyOpenAL (Note: AudioLoader.getXXX calls init for you).
	 * 
	 * @author davedes (http://slick.ninjacave.com/forum/viewtopic.php?t=3920)
	 */
	private static void destroyOpenAL() {
		if (!trackExists())
			return;
		stop();

		try {
			// get Music object's (private) Audio object reference
			Field sound = player.getClass().getDeclaredField("sound");
			sound.setAccessible(true);
			Audio audio = (Audio) (sound.get(player));

			// first clear the sources allocated by SoundStore
			int max = SoundStore.get().getSourceCount();
			IntBuffer buf = BufferUtils.createIntBuffer(max);
			for (int i = 0; i < max; i++) {
				int source = SoundStore.get().getSource(i);
				buf.put(source);

				// stop and detach any buffers at this source
				AL10.alSourceStop(source);
				AL10.alSourcei(source, AL10.AL_BUFFER, 0);
			}
			buf.flip();
			AL10.alDeleteSources(buf);
			int exc = AL10.alGetError();
			if (exc != AL10.AL_NO_ERROR) {
				throw new SlickException(
						"Could not clear SoundStore sources, err: " + exc);
			}

			// delete any buffer data stored in memory, too...
			if (audio != null && audio.getBufferID() != 0) {
				buf = BufferUtils.createIntBuffer(1).put(audio.getBufferID());
				buf.flip();
				AL10.alDeleteBuffers(buf);
				exc = AL10.alGetError();
				if (exc != AL10.AL_NO_ERROR) {
					throw new SlickException("Could not clear buffer "
							+ audio.getBufferID()
							+ ", err: "+exc);
				}
			}

			// clear OpenAL
			AL.destroy();

			// reset SoundStore so that next time we create a Sound/Music, it will reinit
			SoundStore.get().clear();

			player = null;
		} catch (Exception e) {
			Log.error("Failed to destroy OpenAL.", e);
		}
	}
}