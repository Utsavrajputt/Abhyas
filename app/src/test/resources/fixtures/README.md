# Parser fixtures

Put real sample PDFs you are allowed to use here (digital + scanned), plus a text dump per PDF
(`<name>.txt`, one page per `\f` form-feed) and the expected question count (`<name>.expected`,
a single integer). `tools`-free: dump text with any PDF tool. The parser tests never invent exam content;
the synthetic tests in `PdfParserTest` only check mechanics with "TEST ..." placeholders.
