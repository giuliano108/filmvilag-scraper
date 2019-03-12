filmvilag-scraper
===

A web scraper for the online archives of the Hungarian film journal [Filmvilág](http://www.filmvilag.hu). Allows downloading the raw content of the resulting articles for a given search query in JSON format, with metadata. Plenty of hacks of course, plus weird safety checks because of inconsistencies in the pages' source codes and sometimes in the content itself.

Usage
===

I'll intend to build a simple web frontend in the future, but it can be used from the REPL for the moment.
E.g.: `(download-articles-to-json "my_articles.json" :cikk_szerzoje "Huckleberry Hound" :lapszam_ev_tol "2012")`
