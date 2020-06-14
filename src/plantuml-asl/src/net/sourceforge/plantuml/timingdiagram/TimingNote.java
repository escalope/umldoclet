/* ========================================================================
 * PlantUML : a free UML diagram generator
 * ========================================================================
 *
 * (C) Copyright 2009-2020, Arnaud Roques
 *
 * Project Info:  http://plantuml.com
 * 
 * If you like this project or if you find it useful, you can support us at:
 * 
 * http://plantuml.com/patreon (only 1$ per month!)
 * http://plantuml.com/paypal
 * 
 * This file is part of PlantUML.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Original Author:  Arnaud Roques
 */
package net.sourceforge.plantuml.timingdiagram;

import net.sourceforge.plantuml.ColorParam;
import net.sourceforge.plantuml.FontParam;
import net.sourceforge.plantuml.ISkinParam;
import net.sourceforge.plantuml.LineBreakStrategy;
import net.sourceforge.plantuml.command.Position;
import net.sourceforge.plantuml.creole.CreoleMode;
import net.sourceforge.plantuml.creole.CreoleParser;
import net.sourceforge.plantuml.creole.Sheet;
import net.sourceforge.plantuml.creole.SheetBlock1;
import net.sourceforge.plantuml.cucadiagram.Display;
import net.sourceforge.plantuml.graphic.FontConfiguration;
import net.sourceforge.plantuml.graphic.HorizontalAlignment;
import net.sourceforge.plantuml.graphic.HtmlColor;
import net.sourceforge.plantuml.graphic.StringBounder;
import net.sourceforge.plantuml.skin.rose.Rose;
import net.sourceforge.plantuml.svek.image.Opale;
import net.sourceforge.plantuml.ugraphic.UGraphic;

public class TimingNote {

	private final TimeTick when;
	private final Player player;
	private final Display note;
	private final Position position;
	private final ISkinParam skinParam;

	public TimingNote(TimeTick when, Player player, Display note, Position position, ISkinParam skinParam) {
		this.note = note;
		this.player = player;
		this.when = when;
		this.skinParam = skinParam;
		this.position = position;
	}

	public void drawU(UGraphic ug) {
		final Opale opale = createOpale();
		opale.drawU(ug);

	}

	private Opale createOpale() {
		final FontConfiguration fc = new FontConfiguration(skinParam, FontParam.NOTE, null);
		final Rose rose = new Rose();

		final HtmlColor noteBackgroundColor = rose.getHtmlColor(skinParam, ColorParam.noteBackground);
		final HtmlColor borderColor = rose.getHtmlColor(skinParam, ColorParam.noteBorder);

		final Sheet sheet = new CreoleParser(fc, skinParam.getDefaultTextAlignment(HorizontalAlignment.LEFT),
				skinParam, CreoleMode.FULL).createSheet(note);
		final SheetBlock1 sheet1 = new SheetBlock1(sheet, LineBreakStrategy.NONE, skinParam.getPadding());
		final Opale opale = new Opale(borderColor, noteBackgroundColor, sheet1, skinParam.shadowing(null), false);
		return opale;
	}

	public double getHeight(StringBounder stringBounder) {
		return createOpale().calculateDimension(stringBounder).getHeight();
	}

	public TimeTick getWhen() {
		return when;
	}

	public final Position getPosition() {
		return position;
	}

}
