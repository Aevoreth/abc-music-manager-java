package com.aevoreth.abcmm.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProviderContext;
import org.commonmark.renderer.html.AttributeProviderFactory;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Modal dialog that displays the bundled User Guide ({@code docs/user/}) as Markdown.
 */
public final class UserGuideDialog extends JDialog {

    private static final String APP_TITLE = "ABC Music Manager";
    private static final String CLASSPATH_BASE = "/com/aevoreth/abcmm/docs/user/";
    private static final String HOME_PAGE = "index.md";
    private static final Pattern EXPLICIT_HEADING_ID =
            Pattern.compile("\\s*\\{#([\\w.-]+)\\}\\s*$");

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    private final JEditorPane browser = new JEditorPane();
    private final JButton backButton = new JButton("← Back");
    private final JButton forwardButton = new JButton("Forward →");
    private final JButton homeButton = new JButton("User Guide");
    private final Deque<String> backStack = new ArrayDeque<>();
    private final Deque<String> forwardStack = new ArrayDeque<>();
    private String currentRelPath;

    private UserGuideDialog(Window owner) {
        super(owner, titleWithVersion(), ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(560, 420));
        setPreferredSize(new Dimension(720, 560));

        browser.setEditable(false);
        browser.setContentType("text/html");
        browser.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        browser.setFont(browser.getFont().deriveFont(Font.PLAIN, 13f));
        applyBrowserChromeColors();
        browser.addHyperlinkListener(this::onHyperlink);

        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
        backButton.addActionListener(e -> goBack());
        forwardButton.addActionListener(e -> goForward());
        homeButton.addActionListener(e -> navigateTo(HOME_PAGE, null, true));

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        nav.add(backButton);
        nav.add(forwardButton);
        nav.add(homeButton);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        south.add(close);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(nav, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(browser);
        Color paneBg = uiColor("TextPane.background", "Panel.background", Color.WHITE);
        scroll.getViewport().setBackground(paneBg);
        scroll.getViewport().setOpaque(true);
        root.add(scroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                navigateTo(HOME_PAGE, null, false);
            }
        });
    }

    /** Open the user guide. */
    public static void open(Window owner) {
        if (!guideAvailable()) {
            JOptionPane.showMessageDialog(
                    owner,
                    "User Guide not found. See docs/user/ in the repository.",
                    "User Guide",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        UserGuideDialog dialog = new UserGuideDialog(owner);
        dialog.setVisible(true);
    }

    static boolean guideAvailable() {
        return readPage(HOME_PAGE) != null;
    }

    private static String titleWithVersion() {
        String version = AppInfo.implementationVersion();
        if (version == null) {
            return APP_TITLE + " — User Guide";
        }
        return APP_TITLE + " — User Guide (v" + version + ")";
    }

    private void onHyperlink(HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
            return;
        }
        String desc = event.getDescription();
        if (desc == null || desc.isBlank()) {
            return;
        }
        String trimmed = desc.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            openExternal(trimmed);
            return;
        }
        if (trimmed.startsWith("#")) {
            scrollToFragment(trimmed.substring(1));
            return;
        }
        String pathPart = trimmed;
        String fragment = null;
        int hash = trimmed.indexOf('#');
        if (hash >= 0) {
            pathPart = trimmed.substring(0, hash);
            fragment = trimmed.substring(hash + 1);
        }
        if (pathPart.isBlank()) {
            if (fragment != null) {
                scrollToFragment(fragment);
            }
            return;
        }
        String resolved = resolveRelative(currentRelPath, pathPart);
        if (resolved == null || !isAllowed(resolved) || readPage(resolved) == null) {
            return;
        }
        navigateTo(resolved, fragment, true);
    }

    private void navigateTo(String relPath, String fragment, boolean recordHistory) {
        String markdown = readPage(relPath);
        if (markdown == null) {
            return;
        }
        if (recordHistory && currentRelPath != null) {
            backStack.push(currentRelPath);
            forwardStack.clear();
        }
        currentRelPath = normalizeRel(relPath);
        applyBrowserChromeColors();
        browser.setText(toHtml(markdown));
        browser.setCaretPosition(0);
        applyBrowserChromeColors();
        updateNavButtons();
        if (fragment != null && !fragment.isBlank()) {
            SwingUtilities.invokeLater(() -> scrollToFragment(fragment));
        }
    }

    private void goBack() {
        if (backStack.isEmpty() || currentRelPath == null) {
            return;
        }
        forwardStack.push(currentRelPath);
        navigateTo(backStack.pop(), null, false);
        updateNavButtons();
    }

    private void goForward() {
        if (forwardStack.isEmpty() || currentRelPath == null) {
            return;
        }
        backStack.push(currentRelPath);
        navigateTo(forwardStack.pop(), null, false);
        updateNavButtons();
    }

    private void updateNavButtons() {
        backButton.setEnabled(!backStack.isEmpty());
        forwardButton.setEnabled(!forwardStack.isEmpty());
    }

    private void scrollToFragment(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return;
        }
        Element element = findElementById(fragment);
        if (element != null) {
            try {
                browser.scrollRectToVisible(browser.modelToView2D(element.getStartOffset()).getBounds());
                browser.setCaretPosition(element.getStartOffset());
                return;
            } catch (BadLocationException ignored) {
                // fall through
            }
        }
        browser.scrollToReference(fragment);
    }

    private Element findElementById(String id) {
        if (!(browser.getDocument() instanceof HTMLDocument doc)) {
            return null;
        }
        return doc.getElement(id);
    }

    private void applyBrowserChromeColors() {
        Color bg = uiColor("TextPane.background", "Panel.background", Color.WHITE);
        Color fg = uiColor("TextPane.foreground", "Label.foreground", Color.BLACK);
        browser.setBackground(bg);
        browser.setForeground(fg);
        browser.setOpaque(true);
    }

    private static String toHtml(String markdown) {
        Node document = PARSER.parse(markdown);
        Map<Heading, String> headingIds = new IdentityHashMap<>();
        stripExplicitHeadingIds(document, headingIds);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(EXTENSIONS)
                .attributeProviderFactory(headingIdFactory(headingIds))
                .build();
        String body = renderer.render(document);
        return "<html><head><style>\n" + themeCss() + "\n</style></head><body>\n"
                + body
                + "\n</body></html>";
    }

    /** CSS colors derived from the active FlatLaf (dark or light). */
    private static String themeCss() {
        boolean dark = AbcmmThemer.isDarkMode();
        Color bg = uiColor("TextPane.background", "Panel.background",
                dark ? new Color(0x2b2b2b) : Color.WHITE);
        Color fg = uiColor("TextPane.foreground", "Label.foreground",
                dark ? new Color(0xdddddd) : new Color(0x222222));
        Color muted = uiColor("Label.disabledForeground", "Component.borderColor",
                dark ? new Color(0xaaaaaa) : new Color(0x555555));
        Color border = uiColor("Component.borderColor", "Separator.foreground",
                dark ? new Color(0x666666) : new Color(0x888888));
        Color tableHeader = uiColor("TableHeader.background", "Panel.background",
                dark ? new Color(0x3c3f41) : new Color(0xe8e8e8));
        Color codeBg = uiColor("TextField.background", "Panel.background",
                dark ? new Color(0x3c3f41) : new Color(0xf4f4f4));
        Color link = dark ? new Color(0x6cb6ff) : new Color(0x0645ad);
        Color blockBorder = border;

        return """
                body { font-family: sans-serif; font-size: 13pt; margin: 12px; \
                color: %s; background-color: %s; }
                h1 { font-size: 1.6em; color: %s; }
                h2 { font-size: 1.3em; margin-top: 1.2em; color: %s; }
                h3 { font-size: 1.15em; color: %s; }
                p, li, td { color: %s; }
                table { border-collapse: collapse; margin: 0.8em 0; }
                th, td { border: 1px solid %s; padding: 4px 8px; vertical-align: top; }
                th { background: %s; color: %s; }
                td { background: %s; }
                code { font-family: monospace; background: %s; color: %s; }
                pre { background: %s; color: %s; padding: 8px; }
                blockquote { border-left: 3px solid %s; margin-left: 0; \
                padding-left: 12px; color: %s; }
                a { color: %s; }
                hr { border: none; border-top: 1px solid %s; }
                """.formatted(
                css(fg), css(bg),
                css(fg), css(fg), css(fg),
                css(fg),
                css(border),
                css(tableHeader), css(fg),
                css(bg),
                css(codeBg), css(fg),
                css(codeBg), css(fg),
                css(blockBorder), css(muted),
                css(link),
                css(border));
    }

    private static Color uiColor(String primaryKey, String fallbackKey, Color defaultColor) {
        Color c = UIManager.getColor(primaryKey);
        if (c != null) {
            return c;
        }
        c = UIManager.getColor(fallbackKey);
        return c != null ? c : defaultColor;
    }

    private static String css(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static AttributeProviderFactory headingIdFactory(Map<Heading, String> headingIds) {
        return (AttributeProviderContext context) -> (node, tagName, attributes) -> {
            if (node instanceof Heading heading) {
                String id = headingIds.get(heading);
                if (id == null || id.isBlank()) {
                    id = slugify(collectHeadingText(heading));
                }
                if (id != null && !id.isBlank()) {
                    attributes.put("id", id);
                }
            }
        };
    }

    private static void stripExplicitHeadingIds(Node document, Map<Heading, String> headingIds) {
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                String full = collectHeadingText(heading);
                Matcher matcher = EXPLICIT_HEADING_ID.matcher(full);
                if (matcher.find()) {
                    headingIds.put(heading, matcher.group(1));
                    String cleaned = matcher.replaceFirst("").trim();
                    replaceHeadingText(heading, cleaned);
                } else {
                    String slug = slugify(full);
                    if (slug != null) {
                        headingIds.put(heading, slug);
                    }
                }
                visitChildren(heading);
            }
        });
    }

    private static String collectHeadingText(Heading heading) {
        StringBuilder sb = new StringBuilder();
        heading.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                sb.append(text.getLiteral());
            }
        });
        return sb.toString();
    }

    private static void replaceHeadingText(Heading heading, String cleaned) {
        List<Node> children = new ArrayList<>();
        for (Node child = heading.getFirstChild(); child != null; child = child.getNext()) {
            children.add(child);
        }
        for (Node child : children) {
            child.unlink();
        }
        heading.appendChild(new Text(cleaned));
    }

    private static String slugify(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static void openExternal(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // Best-effort external open
        }
    }

    static String resolveRelative(String currentRel, String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String cleaned = href.replace('\\', '/');
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        Path baseDir = currentRel == null || currentRel.isBlank()
                ? Paths.get("")
                : Paths.get(currentRel).getParent();
        if (baseDir == null) {
            baseDir = Paths.get("");
        }
        Path resolved = baseDir.resolve(cleaned).normalize();
        String asString = resolved.toString().replace('\\', '/');
        if (asString.startsWith("../") || asString.equals("..")) {
            return null;
        }
        return normalizeRel(asString);
    }

    static boolean isAllowed(String relPath) {
        if (relPath == null || relPath.isBlank()) {
            return false;
        }
        String n = normalizeRel(relPath);
        return !n.startsWith("../") && !n.contains("..");
    }

    private static String normalizeRel(String relPath) {
        return relPath.replace('\\', '/');
    }

    static String readPage(String relPath) {
        if (!isAllowed(relPath)) {
            return null;
        }
        String normalized = normalizeRel(relPath);
        String fromClasspath = readClasspath(normalized);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        return readFilesystem(normalized);
    }

    private static String readClasspath(String relPath) {
        String resource = CLASSPATH_BASE + relPath;
        try (InputStream in = UserGuideDialog.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readFilesystem(String relPath) {
        for (Path root : filesystemRoots()) {
            Path file = root.resolve(relPath).normalize();
            if (!file.startsWith(root.normalize())) {
                continue;
            }
            if (Files.isRegularFile(file)) {
                try {
                    return Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static List<Path> filesystemRoots() {
        List<Path> roots = new ArrayList<>();
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path probe = cwd;
        for (int i = 0; i < 4 && probe != null; i++) {
            Path candidate = probe.resolve("docs").resolve("user");
            if (Files.isDirectory(candidate) && !roots.contains(candidate)) {
                roots.add(candidate);
            }
            probe = probe.getParent();
        }
        return roots;
    }
}
