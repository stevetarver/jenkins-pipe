def call(int count = 12, int delay = 10, Closure action) {

    retry (count) {
        try {
            return action()
        } catch (e) {
            sleep delay
            throw e
        }
    }
}

return this
