package ar.edu.austral.inf.sd.server.api

import ar.edu.austral.inf.sd.server.model.RegisterResponse
import org.springframework.http.ResponseEntity

interface RegisterNodeApiService {

    /**
     * POST /register-node
     * Registra un nuevo nodo
     *
     * @param host  (optional)
     * @param port  (optional)
     * @param uuid  (optional)
     * @param salt  (optional)
     * @param name  (optional)
     * @return Todo bien. Nueva registración. Listo para jugar. (status code 200)
     *         or Un cliente recurrente. Registración existente pero válida. Listo para jugar. (status code 202)
     *         or Tenés algún error de parámetros (status code 400)
     *         or Estás tratando de estafarme... El UUID ya existe pero mandaste la clave incorrecta. (status code 401)
     * @see RegisterNodeApi#registerNode
     */
    fun registerNode(host: kotlin.String?, port: kotlin.Int?, uuid: java.util.UUID?, salt: kotlin.String?, name: kotlin.String?): ResponseEntity<RegisterResponse>
}
