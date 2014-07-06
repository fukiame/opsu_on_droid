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

package itdelatrisu.opsu.states;

import itdelatrisu.opsu.Opsu;
import itdelatrisu.opsu.OsuGroupList;
import itdelatrisu.opsu.OsuParser;
import itdelatrisu.opsu.OszUnpacker;
import itdelatrisu.opsu.SoundController;
import itdelatrisu.opsu.Utils;

import java.io.File;

import org.newdawn.slick.Color;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import org.newdawn.slick.Input;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.state.BasicGameState;
import org.newdawn.slick.state.StateBasedGame;

/**
 * "Splash Screen" state.
 * <p>
 * Loads game resources and enters "Main Menu" state.
 */
public class Splash extends BasicGameState {
	/**
	 * Logo image.
	 */
	private Image logo;

	/**
	 * Whether or not loading has completed.
	 */
	private boolean finished = false;

	// game-related variables
	private int state;
	private GameContainer container;
	private boolean init = false;

	public Splash(int state) {
		this.state = state;
	}

	@Override
	public void init(GameContainer container, StateBasedGame game)
			throws SlickException {
		this.container = container;

		logo = new Image("logo.png");
		logo = logo.getScaledCopy((container.getHeight() / 1.2f) / logo.getHeight());
		logo.setAlpha(0f);

		// load Utils class first (needed in other 'init' methods)
		Utils.init(container, game);
	}

	@Override
	public void render(GameContainer container, StateBasedGame game, Graphics g)
			throws SlickException {
		g.setBackground(Color.black);

		int width = container.getWidth();
		int height = container.getHeight();

		logo.drawCentered(width / 2, height / 2);

		// display progress
		if (Options.isLoadVerbose()) {
			g.setColor(Color.white);
			g.setFont(Utils.FONT_MEDIUM);
			int lineHeight = Utils.FONT_MEDIUM.getLineHeight();

			String unpackedFile = OszUnpacker.getCurrentFileName();
			String parsedFile = OsuParser.getCurrentFileName();
			if (unpackedFile != null) {
				g.drawString(
						String.format("Unpacking... (%d%%)", OszUnpacker.getUnpackerProgress()),
						25, height - 25 - (lineHeight * 2)
				);
				g.drawString(unpackedFile, 25, height - 25 - lineHeight);
			} else if (parsedFile != null) {
				g.drawString(
						String.format("Loading... (%d%%)", OsuParser.getParserProgress()),
						25, height - 25 - (lineHeight * 2)
				);
				g.drawString(parsedFile, 25, height - 25 - lineHeight);
			}
		}
	}

	@Override
	public void update(GameContainer container, StateBasedGame game, int delta)
			throws SlickException {
		if (!init) {
			init = true;

			// load other resources in a new thread
			final int width = container.getWidth();
			final int height = container.getHeight();
			final SongMenu menu = (SongMenu) game.getState(Opsu.STATE_SONGMENU);
			new Thread() {
				@Override
				public void run() {
					File beatmapDir = Options.getBeatmapDir();

					// unpack all OSZ archives
					OszUnpacker.unpackAllFiles(Options.getOSZDir(), beatmapDir);

					// parse song directory
					OsuParser.parseAllFiles(beatmapDir, width, height);

					// initialize song list
					Opsu.groups.init(OsuGroupList.SORT_TITLE);
					menu.setFocus(Opsu.groups.getRandomNode(), -1, true);

					// load sounds
					SoundController.init();

					finished = true;
				}
			}.start();
		}

		// fade in logo
		float alpha = logo.getAlpha();
		if (alpha < 1f)
			logo.setAlpha(alpha + (delta / 400f));

		// change states when loading complete
		if (finished && alpha >= 1f)
			game.enterState(Opsu.STATE_MAINMENU);
	}

	@Override
	public int getID() { return state; }

	@Override
	public void keyPressed(int key, char c) {
		if (key == Input.KEY_ESCAPE) {
			Options.saveOptions();
			Opsu.closeSocket();
			container.exit();
		}
	}
}
