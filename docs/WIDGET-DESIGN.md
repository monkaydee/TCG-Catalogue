# Collection widgets

The existing Portfolio widget now offers Focus (large value), Dashboard (value plus card/sealed/unpriced counts), and Collector (card-inspired art). WidgetConfigureActivity is the launcher configuration activity and can be reopened from the widget's 44dp customize action. Settings opens defaults for new and previously uncustomized widgets. Overrides are stored by Android widget ID, copied on restoration, and removed when a widget is deleted.

Transparency removes the panel and dashboard tile fills entirely; decorative outlines remain. Text color applies to all text and the customize icon. A shared Canvas renderer uses the app's Inter fonts for both live previews and actual Glance output. Layouts use actual launcher dimensions, fit amounts without ellipses, and expose the complete data through the image's accessibility description. The widget itself opens CardNavo.

PortfolioData keeps the existing valuation rules: only known values enter the total, missing copies are visible, and missing prices suppress the 30-day comparison. Dashboard includes sealed counts. No sample values or made-up price charts appear in production widgets.

Robolectric rendering checks cover three layouts at compact/square/wide sizes, transparent pixels, selected font color, independent preference persistence, and configuration controls. Screenshots are uploaded by the existing build workflow.
