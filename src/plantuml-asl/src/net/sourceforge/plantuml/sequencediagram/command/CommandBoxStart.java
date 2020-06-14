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
package net.sourceforge.plantuml.sequencediagram.command;

import net.sourceforge.plantuml.LineLocation;
import net.sourceforge.plantuml.command.CommandExecutionResult;
import net.sourceforge.plantuml.command.SingleLineCommand2;
import net.sourceforge.plantuml.command.regex.IRegex;
import net.sourceforge.plantuml.command.regex.RegexConcat;
import net.sourceforge.plantuml.command.regex.RegexLeaf;
import net.sourceforge.plantuml.command.regex.RegexOptional;
import net.sourceforge.plantuml.command.regex.RegexOr;
import net.sourceforge.plantuml.command.regex.RegexResult;
import net.sourceforge.plantuml.cucadiagram.Display;
import net.sourceforge.plantuml.graphic.color.ColorParser;
import net.sourceforge.plantuml.graphic.color.ColorType;
import net.sourceforge.plantuml.graphic.color.Colors;
import net.sourceforge.plantuml.sequencediagram.SequenceDiagram;

public class CommandBoxStart extends SingleLineCommand2<SequenceDiagram> {

	public CommandBoxStart() {
		super(getRegexConcat());
	}

	static IRegex getRegexConcat() {
		return RegexConcat.build(CommandBoxStart.class.getName(), RegexLeaf.start(), //
				new RegexLeaf("box"), //
				new RegexOptional(new RegexOr( //
						new RegexConcat( //
								RegexLeaf.spaceOneOrMore(), //
								new RegexLeaf("NAME1", "[%g]([^%g]+)[%g]")), //
						new RegexConcat( //
								RegexLeaf.spaceOneOrMore(), //
								new RegexLeaf("NAME2", "([^#]+)")))), //
				RegexLeaf.spaceZeroOrMore(), //
				color().getRegex(), RegexLeaf.end());
	}

	private static ColorParser color() {
		return ColorParser.simpleColor(ColorType.BACK);
	}

	@Override
	protected CommandExecutionResult executeArg(SequenceDiagram diagram, LineLocation location, RegexResult arg2) {
		if (diagram.isBoxPending()) {
			return CommandExecutionResult.error("Box cannot be nested");
		}
		final String argTitle = arg2.getLazzy("NAME", 0);
		final String argColor = arg2.get("COLOR", 0);
		// final HtmlColor color = diagram.getSkinParam().getIHtmlColorSet().getColorIfValid(argColor);
		Colors colors = color().getColor(arg2, diagram.getSkinParam().getIHtmlColorSet());
		final String title = argTitle == null ? "" : argTitle;
		diagram.boxStart(Display.getWithNewlines(title), colors.getColor(ColorType.BACK));
		return CommandExecutionResult.ok();
	}

}
