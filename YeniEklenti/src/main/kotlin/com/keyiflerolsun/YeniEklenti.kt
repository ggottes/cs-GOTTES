// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class TMDB : MainAPI() {
    override var mainUrl              = "https://www.themoviedb.org"
    override var name                 = "TMDB"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 200L
    override var sequentialMainPageScrollDelay = 200L

    override val mainPage = mainPageOf(
        "${mainUrl}/movie/popular" to "Popüler Filmler",
        "${mainUrl}/movie/now-playing" to "Şu Anda Oynatılan",
        "${mainUrl}/movie/upcoming" to "Yakında Gelecek",
        "${mainUrl}/movie/top-rated" to "En Çok Beğenilen Filmler",
        "${mainUrl}/tv/popular" to "Popüler Diziler",
        "${mainUrl}/tv/airing-today" to "Bugün Yayınlanan",
        "${mainUrl}/tv/on-the-air" to "TV'de Yayında",
        "${mainUrl}/tv/top-rated" to "En Çok Beğenilen Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // TMDB ana sayfa içeriğini getir
        val response = app.get("${request.data}?page=${page}")
        val document = response.document
        
        val home = document.select("div.card").mapNotNull { element ->
            val titleElement = element.selectFirst("h2 a, h3 a")
            val title = titleElement?.text()?.trim() ?: return@mapNotNull null
            val url = titleElement?.attr("href") ?: return@mapNotNull null
            val posterElement = element.selectFirst("img")
            val poster = posterElement?.attr("src") ?: posterElement?.attr("data-src") ?: ""
            val year = element.selectFirst(".release_date")?.text()?.trim()?.substringBefore("-")?.trim()
            val rating = element.selectFirst(".vote_average")?.text()?.trim()
            
            val isMovie = request.data.contains("/movie/")
            val tvType = if (isMovie) TvType.Movie else TvType.TvSeries
            
            if (isMovie) {
                newMovieSearchResponse(title, url) {
                    this.posterUrl = fixUrlNull(poster)
                    this.year = year?.toIntOrNull()
                    this.rating = rating?.toFloatOrNull()?.times(10)?.toInt()
                }
            } else {
                newTvSeriesSearchResponse(title, url) {
                    this.posterUrl = fixUrlNull(poster)
                    this.year = year?.toIntOrNull()
                    this.rating = rating?.toFloatOrNull()?.times(10)?.toInt()
                }
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("${mainUrl}/search/multi?query=${query}")
        val document = response.document
        
        return document.select("div.card").mapNotNull { element ->
            val titleElement = element.selectFirst("h2 a, h3 a")
            val title = titleElement?.text()?.trim() ?: return@mapNotNull null
            val url = titleElement?.attr("href") ?: return@mapNotNull null
            val posterElement = element.selectFirst("img")
            val poster = posterElement?.attr("src") ?: posterElement?.attr("data-src") ?: ""
            val year = element.selectFirst(".release_date")?.text()?.trim()?.substringBefore("-")?.trim()
            val rating = element.selectFirst(".vote_average")?.text()?.trim()
            val mediaType = element.attr("data-media-type") ?: ""
            
            val isMovie = mediaType == "movie" || url.contains("/movie/")
            
            if (isMovie) {
                newMovieSearchResponse(title, url) {
                    this.posterUrl = fixUrlNull(poster)
                    this.year = year?.toIntOrNull()
                    this.rating = rating?.toFloatOrNull()?.times(10)?.toInt()
                }
            } else {
                newTvSeriesSearchResponse(title, url) {
                    this.posterUrl = fixUrlNull(poster)
                    this.year = year?.toIntOrNull()
                    this.rating = rating?.toFloatOrNull()?.times(10)?.toInt()
                }
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)
        val document = response.document
        
        val title = document.selectFirst("h2.title, h1.title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("img.poster")?.attr("src") ?: ""
        val plot = document.selectFirst("div.overview")?.text()?.trim() ?: ""
        val year = document.selectFirst("span.release_date")?.text()?.trim()?.substringBefore("-")?.trim()?.toIntOrNull()
        val rating = document.selectFirst("span.vote_average")?.text()?.trim()?.toFloatOrNull()?.times(10)?.toInt()
        val genres = document.select("span.genres a").map { it.text().trim() }
        val actors = document.select("div.cast_item").mapNotNull { actorElement ->
            val name = actorElement.selectFirst("a")?.text()?.trim()
            val character = actorElement.selectFirst("p.character")?.text()?.trim()
            val photo = actorElement.selectFirst("img")?.attr("src")
            if (name != null) Actor(name, fixUrlNull(photo), character) else null
        }
        
        val isMovie = url.contains("/movie/")
        
        if (isMovie) {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie
            ) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = plot
                this.year = year
                this.rating = rating
                this.tags = genres
                addActors(actors)
            }
        } else {
            // TV Series için bölümleri bul
            val episodes = mutableListOf<Episode>()
            document.select("div.episode_item").forEach { episodeElement ->
                val episodeTitle = episodeElement.selectFirst("h4 a")?.text()?.trim() ?: ""
                val episodeUrl = episodeElement.selectFirst("a")?.attr("href") ?: ""
                val episodeNumber = episodeElement.attr("data-episode")?.toIntOrNull() ?: 1
                val seasonNumber = episodeElement.attr("data-season")?.toIntOrNull() ?: 1
                val episodePlot = episodeElement.selectFirst("p.overview")?.text()?.trim() ?: ""
                
                episodes.add(newEpisode(episodeUrl) {
                    this.name = episodeTitle
                    this.season = seasonNumber
                    this.episode = episodeNumber
                    this.plot = episodePlot
                })
            }
            
            return newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = plot
                this.year = year
                this.rating = rating
                this.tags = genres
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("TMDB", "Loading links for: $data")
        
        // TMDB'de direkt video içerik yok, sadece bilgi ve fragmanlar var
        // Bu yüzden kullanıcıyı TMDB sayfasına yönlendiriyoruz
        val response = app.get(data)
        val document = response.document
        
        // Fragman linklerini bul
        val trailerLinks = document.select("a[href*='youtube'], a[href*='vimeo']").mapNotNull { element ->
            val trailerUrl = element.attr("href")
            if (trailerUrl.isNotEmpty()) {
                ExtractorLink(
                    name = "Fragman - ${element.text().trim()}",
                    url = trailerUrl,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = false
                )
            } else null
        }
        
        // Fragman linklerini ekle
        trailerLinks.forEach { callback(it) }
        
        // Eğer fragman yoksa, kullanıcıyı bilgilendir
        if (trailerLinks.isEmpty()) {
            callback(
                ExtractorLink(
                    name = "TMDB Bilgi Sayfası",
                    url = data,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = false
                )
            )
        }
        
        return true
    }
}
