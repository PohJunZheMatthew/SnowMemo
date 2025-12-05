package Main.Node.TextNode;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownFileInterpret {

    static class TextSegment {
        String type;
        String content;

        TextSegment(String type, String content) {
            this.type = type;
            this.content = content;
        }
    }

    public static String interpret(String text) {
        String[] lines = text.split("\n");
        JSONArray jsonArray = new JSONArray();

        boolean inCodeBlock = false;
        StringBuilder codeContent = new StringBuilder();

        for (String line : lines) {
            // Handle code blocks
            if (line.trim().startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeContent = new StringBuilder();
                    continue;
                } else {
                    // End of code block
                    JSONObject lineObj = new JSONObject();
                    lineObj.put("C_0", codeContent.toString());
                    jsonArray.put(lineObj);
                    inCodeBlock = false;
                    codeContent = new StringBuilder();
                    continue;
                }
            }

            if (inCodeBlock) {
                if (codeContent.length() > 0) {
                    codeContent.append("\n");
                }
                codeContent.append(line);
                continue;
            }

            JSONObject lineObj = new JSONObject();

            // Check for headings
            if (line.trim().startsWith("#")) {
                int headingLevel = 0;
                int i = 0;
                while (i < line.length() && line.charAt(i) == '#') {
                    headingLevel++;
                    i++;
                }

                String headingText = line.substring(headingLevel).trim();
                String headingKey = "h" + headingLevel + "_0";
                lineObj.put(headingKey, headingText);
                jsonArray.put(lineObj);
                continue;
            }

            // Check for unordered lists
            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                JSONArray listArray = new JSONArray();

                // Collect all consecutive list items
                List<String> listItems = new ArrayList<>();
                listItems.add(line.trim().substring(2));

                JSONObject listItemObj = parseLineWithFormatting(line.trim().substring(2));
                listArray.put(listItemObj);

                lineObj.put("L", listArray);
                jsonArray.put(lineObj);
                continue;
            }

            // Check for ordered lists
            if (line.trim().matches("^\\d+\\.\\s.*")) {
                JSONArray listArray = new JSONArray();

                String listText = line.trim().replaceFirst("^\\d+\\.\\s", "");
                JSONObject listItemObj = parseLineWithFormatting(listText);
                listArray.put(listItemObj);

                lineObj.put("OL", listArray);
                jsonArray.put(lineObj);
                continue;
            }

            // Regular text line with inline formatting
            if (!line.trim().isEmpty()) {
                lineObj = parseLineWithFormatting(line);
                jsonArray.put(lineObj);
            }
        }

        return jsonArray.toString(2);
    }

    private static JSONObject parseLineWithFormatting(String line) {
        JSONObject lineObj = new JSONObject();
        List<TextSegment> segments = new ArrayList<>();

        int pos = 0;
        StringBuilder currentText = new StringBuilder();

        while (pos < line.length()) {
            // Check for ***text*** (bold + italic)
            if (pos + 2 < line.length() &&
                    line.charAt(pos) == '*' &&
                    line.charAt(pos + 1) == '*' &&
                    line.charAt(pos + 2) == '*') {

                // Save any accumulated normal text
                if (currentText.length() > 0) {
                    segments.add(new TextSegment("N", currentText.toString()));
                    currentText = new StringBuilder();
                }

                int endPos = line.indexOf("***", pos + 3);
                if (endPos != -1) {
                    String content = line.substring(pos + 3, endPos);
                    segments.add(new TextSegment("BI", content));
                    pos = endPos + 3;
                    continue;
                }
            }

            // Check for **text** (bold)
            if (pos + 1 < line.length() &&
                    line.charAt(pos) == '*' &&
                    line.charAt(pos + 1) == '*') {

                if (currentText.length() > 0) {
                    segments.add(new TextSegment("N", currentText.toString()));
                    currentText = new StringBuilder();
                }

                int endPos = line.indexOf("**", pos + 2);
                if (endPos != -1) {
                    String content = line.substring(pos + 2, endPos);
                    segments.add(new TextSegment("B", content));
                    pos = endPos + 2;
                    continue;
                }
            }

            // Check for *text* or _text_ (italic)
            if (line.charAt(pos) == '*' || line.charAt(pos) == '_') {
                char marker = line.charAt(pos);

                if (currentText.length() > 0) {
                    segments.add(new TextSegment("N", currentText.toString()));
                    currentText = new StringBuilder();
                }

                int endPos = line.indexOf(marker, pos + 1);
                if (endPos != -1) {
                    String content = line.substring(pos + 1, endPos);
                    segments.add(new TextSegment("I", content));
                    pos = endPos + 1;
                    continue;
                }
            }

            // Check for `code`
            if (line.charAt(pos) == '`') {
                if (currentText.length() > 0) {
                    segments.add(new TextSegment("N", currentText.toString()));
                    currentText = new StringBuilder();
                }

                int endPos = line.indexOf('`', pos + 1);
                if (endPos != -1) {
                    String content = line.substring(pos + 1, endPos);
                    segments.add(new TextSegment("C", content));
                    pos = endPos + 1;
                    continue;
                }
            }

            // Regular character
            currentText.append(line.charAt(pos));
            pos++;
        }

        // Add any remaining text
        if (currentText.length() > 0) {
            segments.add(new TextSegment("N", currentText.toString()));
        }

        // Convert segments to JSON with object IDs
        int[] typeCounts = new int[256]; // Track count for each type

        for (TextSegment segment : segments) {
            String typeKey = segment.type.charAt(0) + "";
            if (segment.type.length() > 1) {
                typeKey = segment.type;
            }

            int count = typeCounts[typeKey.hashCode() % 256];
            String key = segment.type + "_" + count;
            lineObj.put(key, segment.content);
            typeCounts[typeKey.hashCode() % 256]++;
        }

        return lineObj;
    }

    public static void main(String[] args) {
        String markdown = "### Hello\n" +
                "## Contents\n" +
                "### Vegetable\n" +
                "##### Heading 5\n" +
                "###### 67...\n" +
                "This is a regular text\n" +
                "**This is a bold text**\n" +
                "*This is an italic text*\n" +
                "***This is a bold and an italic text***\n" +
                "```\n" +
                "#This is a python code to be displayed as code\n" +
                "print(\"Hello, world\")\n" +
                "```\n" +
                "This is **bold** and this is *italic* text\n" +
                "- This is the starting of a list **,this is a bold text**\n" +
                "1. This will be ordered the first\n" +
                "2. This is the second";

        String result = interpret(markdown);
        System.out.println(result);
    }
}