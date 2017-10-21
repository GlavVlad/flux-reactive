package com.github.glavvlad.fluxreactive

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.http.MediaType.TEXT_EVENT_STREAM
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.core.userdetails.MapUserDetailsRepository
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux
import reactor.core.publisher.SynchronousSink
import java.time.Duration
import java.util.*

@SpringBootApplication
class FluxReactiveApplication {
    @Bean
    fun runner(movieRepository: MovieRepository) = ApplicationRunner {
        val movies = Flux.just("Terminator 2", "Javatar", "Alien", "Predator")
                .flatMap { movieRepository.save(Movie(title = it)) }
        movieRepository.deleteAll()
                .thenMany(movies)
                .thenMany(movieRepository.findAll())
                .subscribe({ println(it) })
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(FluxReactiveApplication::class.java, *args)
}

@Configuration
@EnableWebFluxSecurity
class SecurityConfiguration {

    @Bean
    fun users() = MapUserDetailsRepository(User.withUsername("Vlad").password("pass").roles("ADMIN", "USER").build(),
            User.withUsername("Ivan").password("pass").roles("USER").build())
}

@Service
class MovieService(private val movieRepository: MovieRepository) {

    fun all() = movieRepository.findAll()

    fun byId(id: String) = movieRepository.findById(id)

    fun events(id: String) = Flux.generate({ sink: SynchronousSink<MovieEvent> -> sink.next(MovieEvent(id, Date())) })
            .delayElements(Duration.ofSeconds(1L))
}

@Configuration
class WebConfiguration(val movieService: MovieService) {

    @Bean
    fun routes() = router {
        GET("/movies", { ServerResponse.ok().body(movieService.all(), Movie::class.java) })
        GET("/movies/{id}", { ServerResponse.ok().body(movieService.byId(it.pathVariable("id")), Movie::class.java) })
        GET("/movies/{id}/events", {
            ServerResponse.ok().contentType(TEXT_EVENT_STREAM)
                    .body(movieService.events(it.pathVariable("id")), MovieEvent::class.java)
        })
    }
}

//@RestController
//class MovieController(var movieService: MovieService) {
//
//    @GetMapping("movies")
//    fun all() = movieService.all()
//
//    @GetMapping("movies/{id}")
//    fun byId(@PathVariable id: String) = movieService.byId(id)
//
//    @GetMapping("movies/{id}/events", produces = arrayOf(TEXT_EVENT_STREAM_VALUE))
//    fun events(@PathVariable id: String) = movieService.events(id)
//}

@Document
data class Movie(var id: String? = null, var title: String? = null)

data class MovieEvent(var movieId: String? = null, var date: Date? = null)

interface MovieRepository : ReactiveMongoRepository<Movie, String>
