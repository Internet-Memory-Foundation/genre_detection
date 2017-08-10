package net.internetmemory.util.scraping;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

//import com.steadystate.css.parser.CSSOMParser;
//import net.internetmemory.util.scraping.thirdparty.W3CDom;
//import org.apache.xerces.parsers.DOMParser;
//import org.cyberneko.html.HTMLConfiguration;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.mozilla.intl.chardet.HtmlCharsetDetector;
import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsICharsetDetectionObserver;
import org.mozilla.intl.chardet.nsPSMDetector;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.ErrorHandler;
import org.w3c.dom.Document;
import org.w3c.dom.css.CSSImportRule;
import org.w3c.dom.css.CSSRule;
import org.w3c.dom.css.CSSRuleList;
import org.w3c.dom.css.CSSStyleSheet;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * A utility class providing common functionality for parsing / cleaning and comparing HTML
 * documents for Web scraping.
 * <b>Important note: do not use this class for other purposes.</b> Algorithm implemented
 * in this class is specialized only for data extraction done by WebScrapingUtils library.
 * To keep consistency of annotations and wrappers, <b>do not modify this class to reuse
 * this class for other purpose</b>
 */
public class HtmlUtils {

    private static final Logger log = LoggerFactory.getLogger(HtmlUtils.class);

    private HtmlUtils() {
    }

    private static final int NUM_BYTES_TO_DETECT_ENCODING = 1024 * 128;
    private static final String DEFAULT_ENCODING = "UTF-8";

    private static final Map<String, String> ENCODING_NAME_FROM_MOZILLA_TO_JDK =
            Collections.unmodifiableMap(new HashMap<String, String>() {{
                put("ISO-2022-CN", "ISO2022CN");
                put("BIG5", "Big5");
                put("EUC-TW", "EUC_TW");
                put("GB18030", "GB18030");
                put("ISO-8859-5", "ISO8859_5");
                put("KOI8-R", "KOI8_R");
                put("WINDOWS-1251", "Cp1251");
                put("MACCYRILLIC", "MacCyrillic");
                put("IBM866", "Cp866");
                put("IBM855", "Cp855");
                put("ISO-8859-7", "ISO8859_7");
                put("WINDOWS-1253", "Cp1253");
                put("ISO-8859-8", "ISO8859_8");
                put("WINDOWS-1255", "Cp1255");
                put("ISO-2022-JP", "ISO2022JP");
                put("SHIFT_JIS", "SJIS");
                put("EUC-JP", "EUC_JP");
                put("ISO-2022-KR", "ISO2022KR");
                put("EUC-KR", "EUC_KR");
                put("UTF-8", "UTF-8");
                put("UTF-16BE", "UnicodeBigUnmarked");
                put("UTF-16LE", "UnicodeLittleUnmarked");
                put("UTF-32BE", "UTF_32BE");
                put("UTF-32LE", "UTF_32LE");
                put("WINDOWS-1252", "Cp1252");
            }});

    /**
     * Detects character encoding of the specified HTML document
     *
     * @param html byte array representation of the HTML document to analyze
     * @return the name (canonical for java.lang) of a detected character encoding of the HTML document
     */
    public static String detectEncoding(byte[] html) {

        final int numBytesToUse = html.length > NUM_BYTES_TO_DETECT_ENCODING ?
                NUM_BYTES_TO_DETECT_ENCODING : html.length;

        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(html, 0, numBytesToUse);
        detector.dataEnd();

        String detected = detector.getDetectedCharset();
        if (detected != null)
            detected = ENCODING_NAME_FROM_MOZILLA_TO_JDK.get(detected);

        return detected != null ? detected : DEFAULT_ENCODING;
    }


    /**
     * Alternative detect encoding, should be better than the other
     * @param html
     * @return
     */
    public static String detectEncodingAlt(byte[] html) {
        // Initalize the nsDetector() ;
        int lang = nsPSMDetector.ALL ;
        nsDetector det = new nsDetector(lang) ;

        // Set an observer...
        // The Notify() will be called when a matching charset is found.
        det.Init(new nsICharsetDetectionObserver() {
            public void Notify(String charset) {
//                HtmlCharsetDetector.found = true ;
//                System.out.println("CHARSET = " + charset);
            }
        });

        ByteArrayInputStream imp = new ByteArrayInputStream(html);
//        BufferedInputStream imp = new BufferedInputStream(url.openStream());

        byte[] buf = new byte[1024] ;
        int len;
        boolean done = false ;
        boolean isAscii = true ;
        int counter = 0;
        while( (len=imp.read(buf,0,buf.length)) != -1) {

            // Check if the stream is only ascii.
            if (isAscii)
                isAscii = det.isAscii(buf,len);

            // DoIt if non-ascii and not done yet.
            if (!isAscii && !done) {
//                log.info("iteration: "+counter++);
                //done =
                det.DoIt(buf, len, false);
            }
        }
        det.DataEnd();

        String[] probableCharsets = det.getProbableCharsets();

//        System.out.println(Arrays.asList(probableCharsets));
//        if (isAscii) {
//            System.out.println("CHARSET = ASCII");
//            found = true ;
//        }
        String charset = probableCharsets[0];
        if (!charset.equals("nomatch")) {
            return probableCharsets[0];
        }
        return DEFAULT_ENCODING;
    }

    private static final List<String> HTML_EVENT_ATTRIBUTES = Collections.unmodifiableList(
            Arrays.asList(
                    "onafterprint", "onbeforeprint", "onbeforeunload", "onerror",
                    "onhaschange", "onload", "onmessage", "onoffline",
                    "ononline", "onpagehide", "onpageshow", "onpopstate",
                    "onredo", "onresize", "onstorage", "onundo",
                    "onunload", "onblur", "onchange", "oncontextmenu",
                    "onfocus", "onformchange", "onforminput", "oninput",
                    "oninvalid", "onreset", "onselect", "onsubmit",
                    "onkeydown", "onkeypress", "onkeyup", "onclick",
                    "ondblclick", "ondrag", "ondragend", "ondragenter",
                    "ondragleave", "ondragover", "ondragstart", "ondrop",
                    "onmousedown", "onmousemove", "onmouseout", "onmouseover",
                    "onmouseup", "onmousewheel", "onscroll", "onabort",
                    "oncanplay", "oncanplaythrough", "ondurationchange", "onemptied",
                    "onended", "onerror", "onloadeddata", "onloadedmetadata",
                    "onloadstart", "onpause", "onplay", "onplaying",
                    "onprogress", "onratechange", "onreadystatechange", "onseeked",
                    "onseeking", "onstalled", "onsuspend", "ontimeupdate",
                    "onvolumechange", "onwaiting",
                    "http-equiv" // <- not event attribute, but causes page redirect with <META>
            )
    );

    private static final Map<String, List<String>> HTML_URL_ATTRIBUTES = Collections.unmodifiableMap(
            new HashMap<String, List<String>>() {{
                put("a", Arrays.asList("href"));
                put("applet", Arrays.asList("archive", "codebase"));
                put("area", Arrays.asList("href"));
                put("audio", Arrays.asList("src"));
                put("base", Arrays.asList("href"));
                put("blockquote", Arrays.asList("cite"));
                put("button", Arrays.asList("formaction"));
                put("command", Arrays.asList("icon"));
                put("del", Arrays.asList("cite"));
                put("embed", Arrays.asList("src"));
                put("form", Arrays.asList("action"));
                put("frame", Arrays.asList("longdesc", "src"));
                put("head", Arrays.asList("profile"));
                put("html", Arrays.asList("manifest"));
                put("iframe", Arrays.asList("longdesc", "src"));
                put("img", Arrays.asList("longdesc", "src", "usemap"));
                put("input", Arrays.asList("formaction", "src", "usemap"));
                put("ins", Arrays.asList("cite"));
                put("link", Arrays.asList("href"));
                put("object", Arrays.asList("archive", "classid", "codebase", "data", "usemap"));
                put("q", Arrays.asList("cite"));
                put("script", Arrays.asList("src"));
                put("source", Arrays.asList("src"));
                put("video", Arrays.asList("poster", "src"));
                put("meta", Arrays.asList("http-equiv"));
            }}
    );

    private static final List<String> HTML_FRAME_ELEMENTS = Collections.unmodifiableList(Arrays.asList(
            "frame", "iframe"
    ));

    private static final Pattern WHITE_SPACE = Pattern.compile("\\s");

    /**
     * Appends the given string to the specified string buffer with white space normalization.
     * @param builder the StringBuilder object
     * @param text the text to append
     * @param afterWhiteSpace true if the text is after white space
     * @return true if the appended text is terminated by a white space character
     */
    public static boolean normalizeWhiteSpace(StringBuilder builder, String text, boolean afterWhiteSpace){

        int length = text.length();
        for(int i = 0; i < length; i++){
            char c = text.charAt(i);
            if(Character.isWhitespace(c) || c == 160){
                afterWhiteSpace = true;
                continue;
            }

            if(afterWhiteSpace && builder.length() > 0) builder.append(' ');
            afterWhiteSpace = false;
            builder.append(c);
        }

        return afterWhiteSpace;
    }

    /**
     * Normalizes white spaces in the given string.
     * @param text the text to normalize
     * @return resulting text
     */
    public static String normalizeWhiteSpace(String text){
        StringBuilder builder = new StringBuilder(text.length());
        normalizeWhiteSpace(builder, text, false);
        return builder.toString();
    }




    private static final ErrorHandler slf4jCssErrorHandler = new ErrorHandler() {

        private void log(String prefix, CSSParseException exception) {

            final StringBuilder sb = new StringBuilder();
            sb.append(prefix)
                    .append(" [")
                    .append(exception.getLineNumber())
                    .append(":")
                    .append(exception.getColumnNumber())
                    .append("] ")
                    .append(exception.getMessage());

            log.debug(sb.toString());
        }

        @Override
        public void warning(final CSSParseException exception) throws CSSException {

            log("CSS WARN", exception);
        }

        @Override
        public void error(final CSSParseException exception) throws CSSException {

            log("CSS ERROR", exception);
        }

        @Override
        public void fatalError(final CSSParseException exception) throws CSSException {

            log("CSS FATAL", exception);
        }
    };
}
