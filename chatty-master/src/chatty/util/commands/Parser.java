
package chatty.util.commands;

import chatty.util.Pair;
import chatty.util.SyntaxHighlighter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom Commands parser. Returns an Items object containing all the elements
 * of the Custom Command.
 * 
 * @author tduva
 */
public class Parser {

    private final String input;
    private final StringReader reader;
    
    private String escape = "\\";
    private String special = "$";
    
    public Parser(String text, String special, String escape) {
        input = text;
        reader = new StringReader(text);
        this.special = special;
        this.escape = escape;
    }
    
    private SyntaxHighlighter syntaxHighlighter;
    
    Items parse(SyntaxHighlighter syntaxHighlighter) throws ParseException {
        this.syntaxHighlighter = syntaxHighlighter;
        return parse();
    }
    
    /**
     * Parses a Custom Command.
     * 
     * @return
     * @throws ParseException 
     */
    Items parse() throws ParseException {
        if (special.length() > 1 || escape.length() > 1) {
            error("Special/escape must each be a single character", 0);
        }
        if (special.equals(escape) && !escape.isEmpty()) {
            error("Special and escape must not be equal", 0);
        }
        return parse((String) null);
    }
    
    /**
     * Reads in values until it encounters one of the characters defined in the
     * given regex parameter. It won't include the character that made it stop
     * in the result.
     * 
     * @param to Regex that will make it stop (single-character)
     * @return An Items object containing all the parsed elements
     * @throws ParseException If the parser encountered something unexpected
     */
    private Items parse(String to) throws ParseException {
        Items items = new Items();
        while (reader.hasNext() && (to == null || !reader.peek().matches(to))) {
            if (accept(special)) {
                Item item = specialThing();
                if (to == null) {
                    // Top-level item
                    items.add(new SpecialEscape(item));
                }
                else {
                    items.add(item);
                }
            } else if (accept(escape)) {
                if (reader.hasNext()) {
                    // Just read next character as literal
                    items.add(reader.next());
                }
            } else {
                items.add(reader.next());
            }
        }
        items.flush();
        return items;
    }
    
    private void startHl(int offset, SyntaxHighlighter.Type type) {
        if (syntaxHighlighter != null) {
            syntaxHighlighter.start(reader.pos() + offset, type);
        }
    }
    
    private void endHl() {
        if (syntaxHighlighter != null) {
            syntaxHighlighter.end(reader.pos() + 1);
        }
    }
    
    private void addHl(String character) {
        if (syntaxHighlighter == null || "$".contains(character)) {
            return;
        }
        
        if ("-".contains(character)) {
            syntaxHighlighter.add(reader.pos(), reader.pos() + 1, SyntaxHighlighter.Type.IDENTIFIER);
        }
        else if (character.equals("\\")) {
            syntaxHighlighter.add(reader.pos(), reader.pos() + 1, SyntaxHighlighter.Type.ESCAPE);
        }
        else {
            syntaxHighlighter.add(reader.pos(), reader.pos() + 1, SyntaxHighlighter.Type.REGULAR2);
        }
    }
    
    /**
     * If the parser encountered something unexpected, this will create an error
     * message and throw a ParseException.
     * 
     * @param message
     * @throws ParseException 
     */
    private void error(String message, int offset) throws ParseException {
        throw new ParseException(message, reader.pos() + offset);
    }
    
    /**
     * A single character that the parser can accept as next character, but
     * won't throw an error if it's not there. If the character is indeed there
     * it will be read (advancing the read index), but not returned.
     * 
     * @param character A single character
     * @return true if the character is the next character, false otherwise
     */
    private boolean accept(String character) {
        if (reader.hasNext() && reader.peek().equals(character)) {
            reader.next();
            addHl(character);
            return true;
        }
        return false;
    }
    
    private boolean peek(String character) {
        return reader.hasNext() && reader.peek().equals(character);
    }
    
    private String acceptMatch(String regex) {
        if (reader.hasNext() && reader.peek().matches(regex)) {
            return reader.next();
        }
        return null;
    }
    
    /**
     * A single character that the parser expects to be the next character, and
     * will throw an error if it's not there. This will advance the read index.
     * 
     * @param character A single character
     * @throws ParseException 
     */
    private void expect(String character) throws ParseException {
        if (!reader.hasNext() || !reader.next().equals(character)) {
            error("Expected '"+character+"'", 0);
        }
        addHl(character);
    }
    
    /**
     * Read until it encounters a character not matching the given regex. The
     * character that caused it to stop won't be read.
     *
     * @param regex A regex matching a single character
     * @return The read String, may be empty
     */
    private String readAll(String regex) {
        StringBuilder b = new StringBuilder();
        while (reader.hasNext() && reader.peek().matches(regex)) {
            b.append(reader.next());
        }
        return b.toString();
    }

    /**
     * Read a single character that matches the given regex.
     * 
     * @param regex A regex matching a single character
     * @return The read String, may be empty
     */
    private String readOne(String regex) {
        if (reader.hasNext() && reader.peek().matches(regex)) {
            return reader.next();
        }
        return "";
    }
    
    private static final String QUOTES = "[\"`']";
    
    /**
     * Parse stuff that occurs after a '$'.
     * 
     * @return
     * @throws ParseException 
     */
    private Item specialThing() throws ParseException {
        String quote;
        if ((quote = acceptMatch(QUOTES)) != null) {
            startHl(-1, SyntaxHighlighter.Type.ESCAPE);
            return literal(quote);
        }
        // Not quite sure yet how this feature should work exactly
//        String escapeCharacter = readOne("[^\\Q"+special+"\\Ea-zA-Z0-9()]");
//        if (escapeCharacter.length() == 1) {
//            return changedEscapeCharacter(escapeCharacter);
//        }
        startHl(0, SyntaxHighlighter.Type.REGULAR);
        boolean isRequired = accept(special);
        String type = functionName();
        endHl();
        if (type.isEmpty()) {
            return replacement(isRequired);
        }
        else if (type.equals("if")) {
            return condition(isRequired);
        }
        else if (type.equals("join")) {
            return join(isRequired);
        }
        else if (type.equals("ifeq")) {
            return ifEq(isRequired);
        }
        else if (type.equals("switch")) {
            return switchFunc(isRequired);
        }
        else if (type.equals("lower")) {
            return lower(isRequired);
        }
        else if (type.equals("upper")) {
            return upper(isRequired);
        }
        else if (type.equals("trim")) {
            return trim(isRequired);
        }
        else if (type.equals("rand")) {
            return rand(isRequired);
        }
        else if (type.equals("randnum")) {
            return randNum(isRequired);
        }
        else if (type.equals("datetime")) {
            return datetime(isRequired);
        }
        else if (type.equals("urlencode")) {
            return urlencode(isRequired);
        }
        else if (type.equals("cs")) {
            return escape(Escape.Type.CHAIN, isRequired);
        }
        else if (type.equals("fs")) {
            return escape(Escape.Type.FOREACH, isRequired);
        }
        else if (type.equals("sort")) {
            return sort(isRequired);
        }
        else if (type.equals("replace")) {
            return replace(isRequired);
        }
        else if (type.equals("is")) {
            return is(isRequired);
        }
        else if (type.equals("get")) {
            return get(isRequired);
        }
        else if (type.equals("calc")) {
            return calc(isRequired);
        }
        else if (type.equals("round")) {
            return round(isRequired);
        }
        else if (type.equals("input")) {
            return input(isRequired);
        }
        else if (type.equals("request")) {
            return request(isRequired);
        }
        else if (type.equals("json")) {
            return json(isRequired);
        }
        else if (type.equals("j")) {
            return jsonPath(isRequired);
        }
        else if (type.equals("quote")) {
            return quote(isRequired);
        }
        else {
            error("Invalid function '"+type+"'", 0);
            return null;
        }
    }
    
    /**
     * $(chan) $(3) $(5-) $3 $3-
     * 
     * @return 
     */
    private Item identifier() throws ParseException {
        startHl(1, SyntaxHighlighter.Type.IDENTIFIER);
        String ref = readAll("[a-zA-Z0-9-_]");
        if (ref.isEmpty()) {
            error("Expected identifier", 1);
        }
        Matcher m = Pattern.compile("([0-9]+)(-)?").matcher(ref);
        if (m.matches()) {
            int index = Integer.parseInt(m.group(1));
            if (index == 0) {
                error("Invalid numeric identifier 0", 0);
            }
            boolean toEnd = m.group(2) != null;
            endHl();
            return new RangeIdentifier(index, toEnd);
        } else {
            endHl();
            return new Identifier(ref);
        }
    }
    
    private Item tinyIdentifier() throws ParseException {
        startHl(1, SyntaxHighlighter.Type.IDENTIFIER);
        String ref = readOne("[0-9]");
        endHl();
        if (ref.isEmpty()) {
            error("Expected numeric identifier", 1);
        }
        int index = Integer.parseInt(ref);
        if (index == 0) {
            error("Invalid numeric identifer 0", 0);
        }
        boolean toEnd = false;
        if (accept("-")) {
            toEnd = true;
        }
        return new RangeIdentifier(index, toEnd);
    }

    private Item condition(boolean isRequired) throws ParseException {
        expect("(");
        Item identifier = peekParam();
        expect(",");
        Items output1 = param();
        Items output2 = null;
        if (accept(",")) {
            output2 = lastParam();
        }
        expect(")");
        return new If(identifier, isRequired, output1, output2);
    }
    
    private Item ifEq(boolean isRequired) throws ParseException {
        expect("(");
        Item identifier = peekParam();
        expect(",");
        Items compare = param();
        expect(",");
        Items output1 = param();
        Items output2 = null;
        if (accept(",")) {
            output2 = lastParam();
        }
        expect(")");
        return new IfEq(identifier, isRequired, compare, output1, output2);
    }
    
    private Item switchFunc(boolean isRequired) throws ParseException {
        expect("(");
        Item identifier = peekParam();
        Item def = new Items();
        expect(",");
        Map<Item, Item> cases = new LinkedHashMap<>();
        do {
            Item key = parse("[,):]");
            if (accept(":")) {
                if (cases.containsKey(key)) {
                    error("Duplicate case: "+key, 0);
                }
                cases.put(key, param());
            }
            else {
                // Default case must be the last one
                def = key;
                break;
            }
        } while (accept(","));
        expect(")");
        if (cases.isEmpty()) {
            error("No case found", -1);
        }
        return new Switch(identifier, cases, def, isRequired);
    }
    
    private Item join(boolean isRequired) throws ParseException {
        expect("(");
        Item identifier = peekParam();
        expect(",");
        Items separator = parse("[)]");
        if (separator.isEmpty()) {
            error("Expected separator string", 1);
        }
        expect(")");
        return new Join(identifier, separator, isRequired);
    }
    
    private Item lower(boolean isRequired) throws ParseException {
        expect("(");
        Item identifier = peekParam();
        expect(")");
        return new Lower(identifier, isRequired);
    }
    
    private Item upper(boolean isRequired) throws ParseException {
        expect("(");
        Item identifier = peekParam();
        expect(")");
        return new Upper(identifier, isRequired);
    }
    
    private Item trim(boolean isRequired) throws ParseException {
        expect("(");
        Item identifier = param();
        expect(")");
        return new Trim(identifier, isRequired);
    }
    
    private Item rand(boolean isRequired) throws ParseException {
        expect("(");
        List<Item> params = new ArrayList<>();
        do {
            params.add(param());
        } while(accept(","));
        expect(")");
        return new Rand(isRequired, params);
    }
    
    private Item randNum(boolean isRequired) throws ParseException {
        expect("(");
        Item a = param();
        Item b = null;
        if (accept(",")) {
            b = param();
        }
        expect(")");
        return new RandNum(isRequired, a, b);
    }
    
    private Item datetime(boolean isRequired) throws ParseException {
        expect("(");
        Item format = param();
        Item timezone = null;
        Item locale = null;
        Item timestamp = null;
        if (accept(",")) {
            timezone = param();
        }
        if (accept(",")) {
            locale = param();
        }
        if (accept(",")) {
            timestamp = param();
        }
        expect(")");
        return new DateTime(format, timezone, locale, timestamp, isRequired);
    }
    
    private Item urlencode(boolean isRequired) throws ParseException {
        expect("(");
        Item item = param();
        expect(")");
        return new UrlEncode(item, isRequired);
    }
    
    private Item escape(Escape.Type type, boolean isRequired) throws ParseException {
        expect("(");
        Item item = param();
        expect(")");
        return new Escape(item, type, isRequired);
    }
    
    private Item sort(boolean isRequired) throws ParseException {
        expect("(");
        Item item = param();
        Item type = null;
        if (accept(",")) {
            type = param();
        }
        Item sep = null;
        if (accept(",")) {
            sep = param();
        }
        expect(")");
        return new Sort(item, sep, type, isRequired);
    }
    
    private Item replace(boolean isRequired) throws ParseException {
        expect("(");
        Item item = midParam();
        expect(",");
        Item search = midParam();
        expect(",");
        Item replace = param();
        Item type = null;
        if (accept(",")) {
            type = param();
        }
        expect(")");
        return new Replace(item, search, replace, isRequired, type);
    }
    
    private Item is(boolean isRequired) throws ParseException {
        expect("(");
        Item item = param();
        expect(")");
        return new Is(item, isRequired);
    }
    
    private Item get(boolean isRequired) throws ParseException {
        expect("(");
        Item item = param();
        Item item2 = null;
        if (accept(",")) {
            item2 = param();
        }
        expect(")");
        return new Get(item, item2, isRequired);
    }
    
    private Item calc(boolean isRequired) throws ParseException {
        expect("(");
        Item item = param();
        expect(")");
        return new Calc(item, isRequired);
    }
    
    private Item round(boolean isRequired) throws ParseException {
        expect("(");
        Item item = param();
        Item decimals = null;
        Item roundingMode = null;
        Item minDecimals = null;
        if (accept(",")) {
            decimals = param();
        }
        if (accept(",")) {
            roundingMode = param();
        }
        if (accept(",")) {
            minDecimals = param();
        }
        expect(")");
        return new Round(item, decimals, roundingMode, minDecimals, isRequired);
    }
    
    private Item input(boolean isRequired) throws ParseException {
        expect("(");
        Item message = param();
        Item initial = null;
        Item type = null;
        if (accept(",")) {
            initial = param();
        }
        if (accept(",")) {
            type = param();
        }
        expect(")");
        return new Input(type, message, initial, isRequired);
    }
    
    private Item request(boolean isRequired) throws ParseException {
        expect("(");
        Item url = param();
        List<Item> options = new ArrayList<>();
        if (accept(",")) {
            do {
                options.add(param());
            } while (accept(","));
        }
        expect(")");
        return new Request(url, options, isRequired);
    }
    
    private Item json(boolean isRequired) throws ParseException {
        expect("(");
        Item input = param();
        expect(",");
        Item output = param();
        expect(")");
        return new Json(input, output, isRequired);
    }
    
    private Item jsonPath(boolean isRequired) throws ParseException {
        expect("(");
        Item path = param();
        Item def = null;
        if (accept(",")) {
            def = param();
        }
        List<Pair<Item, Boolean>> subItems = new ArrayList<>();
        while (accept(",")) {
            boolean each = false;
            if (reader.accept("each:")) {
                each = true;
            }
            subItems.add(new Pair<>(param(), each));
        }
        expect(")");
        return new JsonPathItem(path, def, subItems, isRequired);
    }
    
    private Item quote(boolean isRequired) throws ParseException {
        expect("(");
        Item input = param();
        Item quote = null;
        if (accept(",")) {
            quote = param();
        }
        expect(")");
        return new Quote(input, quote, isRequired);
    }
    
    private Replacement replacement(boolean isRequired) throws ParseException {
        if (accept("(")) {
            Item identifier = identifier();
            Item args = null;
            if (accept(",")) {
                args = param();
            }
            expect(")");
            return new Replacement(identifier, args, isRequired);
        }
        else {
            Item identifier = tinyIdentifier();
            return new Replacement(identifier, null, isRequired);
        }
    }
    
    private Item literal(String quote) throws ParseException {
        StringBuilder b = new StringBuilder();
        endHl();
        while (reader.hasNext()) {
            if (reader.peek().equals(quote)) {
                reader.next();
                if (reader.hasNext() && reader.peek().equals(quote)) {
                    b.append(reader.next());
                }
                else {
                    break;
                }
            }
            else {
                b.append(reader.next());
            }
        }
        // The last character isn't necessarily a closing quote
        if (reader.last().matches(QUOTES)) {
            addHl("\\");
        }
        // Ending quote would have already been consumed
        return new Literal(b.toString());
    }
    
    private Item changedEscapeCharacter(String escapeCharacter) throws ParseException {
        String quote = acceptMatch(QUOTES);
        if (quote == null) {
            error("Expected one of: "+QUOTES, 1);
        }
        String currentEscape = escape;
        escape = escapeCharacter;
        Item result = parse(quote);
        expect(quote);
        escape = currentEscape;
        return result;
    }
    
    private String functionName() {
        return readAll("[a-zA-Z]");
    }
    
    private Items param() throws ParseException {
        return parse("[,)]");
    }
    
    /**
     * For parameters that have required parameters following, so they only
     * expect ",", so other stuff like ")" doesn't have to be escaped if used
     * literal.
     * 
     * @return
     * @throws ParseException 
     */
    private Items midParam() throws ParseException {
        return parse("[,]");
    }
    
    private Items lastParam() throws ParseException {
        return parse("[)]");
    }
    
    private Item peekParam() throws ParseException {
        return peek("$") ? param() : identifier();
    }
    

    
    public static void main(String[] args) {
        Identifier id = new Identifier("abc");
        Literal lit = new Literal("abcd");
        Items items = new Items();
        items.add(id);
        items.add(new Identifier("aijofwe"));
        items.add("_ffweffabc");
        items.add(new Join(new Identifier("cheese"), null, true));
        System.out.println(Item.getIdentifiersWithPrefix("_", id, lit, items));
    }
    
}
