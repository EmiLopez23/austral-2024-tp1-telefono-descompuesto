package ar.edu.austral.inf.sd.server.api

import ar.edu.austral.inf.sd.server.model.RegisterResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

import org.springframework.web.bind.annotation.*
import org.springframework.validation.annotation.Validated
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.beans.factory.annotation.Autowired

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

import kotlin.collections.List
import kotlin.collections.Map

@RestController
@Validated
@RequestMapping("\${api.base-path:}")
class RegisterNodeApiController(@Autowired(required = true) val service: RegisterNodeApiService) {


    @RequestMapping(
        method = [RequestMethod.POST],
        value = ["/register-node"],
        produces = ["application/json"]
    )
    fun registerNode( @Valid @RequestParam(value = "host", required = false) host: kotlin.String?, @Valid @RequestParam(value = "port", required = false) port: kotlin.Int?, @Valid @RequestParam(value = "uuid", required = false) uuid: java.util.UUID?, @Valid @RequestParam(value = "salt", required = false) salt: kotlin.String?, @Valid @RequestParam(value = "name", required = false) name: kotlin.String?): ResponseEntity<RegisterResponse> {
        return service.registerNode(host, port, uuid, salt, name)
    }
}
