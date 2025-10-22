version = 1

cloudstream {
    authors     = listOf("ggottes")
    language    = "tr"
    description = "The Movie Database (TMDB) - Milyonlarca film, TV şovu ve kişi keşfedin. Popüler filmler, diziler ve daha fazlası."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TVSeries")
    iconUrl = "https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692cc6f952a2f5e8c7d9b8c2c4.png"
}
