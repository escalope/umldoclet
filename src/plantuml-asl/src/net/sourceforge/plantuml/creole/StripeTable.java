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
package net.sourceforge.plantuml.creole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import net.sourceforge.plantuml.BackSlash;
import net.sourceforge.plantuml.ISkinSimple;
import net.sourceforge.plantuml.LineBreakStrategy;
import net.sourceforge.plantuml.graphic.FontConfiguration;
import net.sourceforge.plantuml.graphic.HorizontalAlignment;
import net.sourceforge.plantuml.graphic.HtmlColor;

public class StripeTable implements Stripe {

	static enum Mode {
		HEADER, NORMAL
	};

	private FontConfiguration fontConfiguration;
	final private ISkinSimple skinParam;
	final private AtomTable table;
	final private Atom marged;
	final private StripeStyle stripeStyle = new StripeStyle(StripeStyleType.NORMAL, 0, '\0');

	public StripeTable(FontConfiguration fontConfiguration, ISkinSimple skinParam, String line) {
		this.skinParam = skinParam;
		this.fontConfiguration = fontConfiguration;
		HtmlColor lineColor = getBackOrFrontColor(line, 1);
		if (lineColor == null) {
			lineColor = fontConfiguration.getColor();
		}
		this.table = new AtomTable(lineColor);
		this.marged = new AtomWithMargin(table, 2, 2);
		analyzeAndAddInternal(line, Mode.HEADER);
	}

	public List<Atom> getAtoms() {
		return Collections.<Atom> singletonList(marged);
	}
	
	public Atom getHeader() {
		return null;
	}

	static Atom asAtom(List<StripeSimple> cells, double padding) {
		final Sheet sheet = new Sheet(HorizontalAlignment.LEFT);
		for (StripeSimple cell : cells) {
			sheet.add(cell);
		}
		return new SheetBlock1(sheet, LineBreakStrategy.NONE, padding);
	}

	private HtmlColor getBackOrFrontColor(String line, int idx) {
		if (CreoleParser.doesStartByColor(line)) {
			final int idx1 = line.indexOf('#');
			final int idx2 = line.indexOf('>');
			if (idx2 == -1) {
				throw new IllegalStateException();
			}
			final String[] color = line.substring(idx1, idx2).split(",");
			if (idx < color.length) {
				return skinParam.getIHtmlColorSet().getColorIfValid(color[idx]);
			}
		}
		return null;
	}

	private String withouBackColor(String line) {
		final int idx2 = line.indexOf('>');
		if (idx2 == -1) {
			throw new IllegalStateException();
		}
		return line.substring(idx2 + 1);
	}

	private static final String hiddenBar = "\uE000";

	private void analyzeAndAddInternal(String line, Mode mode) {
		line = line.replace("\\|", hiddenBar);
		HtmlColor lineBackColor = getBackOrFrontColor(line, 0);
		if (lineBackColor != null) {
			line = withouBackColor(line);
		}
		table.newLine(lineBackColor);
		for (final StringTokenizer st = new StringTokenizer(line, "|"); st.hasMoreTokens();) {
			String v = st.nextToken().replace(hiddenBar.charAt(0), '|');
			HtmlColor cellBackColor = getBackOrFrontColor(v, 0);
			if (cellBackColor != null) {
				v = withouBackColor(v);
			}
			if (mode == Mode.HEADER && v.startsWith("=")) {
				v = v.substring(1);
			}
			final List<String> lines = getWithNewlinesInternal(v);
			final List<StripeSimple> cells = new ArrayList<StripeSimple>();
			for (String s : lines) {
				final StripeSimple cell = new StripeSimple(getFontConfiguration(mode), stripeStyle,
						new CreoleContext(), skinParam, CreoleMode.FULL);
				if (s.startsWith("<r>")) {
					cell.setCellAlignment(HorizontalAlignment.RIGHT);
					s = s.substring("<r>".length());
				}
				cell.analyzeAndAdd(s);
				cells.add(cell);
			}
			table.addCell(asAtom(cells, skinParam.getPadding()), cellBackColor);
		}
	}

	static List<String> getWithNewlinesInternal(String s) {
		final List<String> result = new ArrayList<String>();
		final StringBuilder current = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			if (c == '\\' && i < s.length() - 1) {
				final char c2 = s.charAt(i + 1);
				i++;
				if (c2 == 'n') {
					result.add(current.toString());
					current.setLength(0);
				} else if (c2 == '\\') {
					current.append(c2);
				} else {
					current.append(c);
					current.append(c2);
				}
			} else if (c == BackSlash.hiddenNewLine()) {
				result.add(current.toString());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}
		result.add(current.toString());
		return result;
	}

	private FontConfiguration getFontConfiguration(Mode mode) {
		if (mode == Mode.NORMAL) {
			return fontConfiguration;
		}
		return fontConfiguration.bold();
	}

	public void analyzeAndAddNormal(String line) {
		analyzeAndAddInternal(line, Mode.NORMAL);
	}

}