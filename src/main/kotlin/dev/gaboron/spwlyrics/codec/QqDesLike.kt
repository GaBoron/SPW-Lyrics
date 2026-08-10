package dev.gaboron.spwlyrics.codec

/** QQ Music uses a DES-compatible construction with two historical S-box deviations. */
@OptIn(ExperimentalUnsignedTypes::class)
internal object QqDesLike {
    fun decrypt(input: ByteArray, key: ByteArray): ByteArray {
        require(input.size % 8 == 0 && key.size == 24)
        val schedule = Array(3) { Array(16) { ByteArray(6) } }
        keySchedule(key.copyOfRange(0, 8), schedule[2], true)
        keySchedule(key.copyOfRange(8, 16), schedule[1], false)
        keySchedule(key.copyOfRange(16, 24), schedule[0], true)
        val output = ByteArray(input.size)
        input.indices.step(8).forEach { offset ->
            val block = input.copyOfRange(offset, offset + 8)
            crypt(block, block, schedule[0])
            crypt(block, block, schedule[1])
            crypt(block, block, schedule[2])
            block.copyInto(output, offset)
        }
        return output
    }

    private fun keySchedule(key: ByteArray, schedule: Array<ByteArray>, decrypt: Boolean) {
        val keyPermC = intArrayOf(56,48,40,32,24,16,8,0,57,49,41,33,25,17,9,1,58,50,42,34,26,18,10,2,59,51,43,35)
        val keyPermD = intArrayOf(62,54,46,38,30,22,14,6,61,53,45,37,29,21,13,5,60,52,44,36,28,20,12,4,27,19,11,3)
        val compression = intArrayOf(13,16,10,23,0,4,2,27,14,5,20,9,22,18,11,3,25,7,15,6,26,19,12,1,40,51,30,36,46,54,29,39,50,44,32,47,43,48,38,55,33,52,45,41,49,35,28,31)
        var c = 0u
        var d = 0u
        repeat(28) { index ->
            c = c or bitNum(key, keyPermC[index], 31 - index)
            d = d or bitNum(key, keyPermD[index], 31 - index)
        }
        repeat(16) { round ->
            val shift = SHIFTS[round]
            c = ((c shl shift) or (c shr (28 - shift))) and 0xfffffff0u
            d = ((d shl shift) or (d shr (28 - shift))) and 0xfffffff0u
            val target = if (decrypt) 15 - round else round
            schedule[target].fill(0)
            repeat(24) { index ->
                val byteIndex = index / 8
                schedule[target][byteIndex] = (schedule[target][byteIndex].toInt() or
                    bitNumIntr(c, compression[index], 7 - index % 8).toInt()).toByte()
            }
            for (index in 24 until 48) {
                val byteIndex = index / 8
                schedule[target][byteIndex] = (schedule[target][byteIndex].toInt() or
                    bitNumIntr(d, compression[index] - 27, 7 - index % 8).toInt()).toByte()
            }
        }
    }

    private fun crypt(input: ByteArray, output: ByteArray, key: Array<ByteArray>) {
        val state = initialPermutation(input)
        repeat(15) { round ->
            val temporary = state[1]
            state[1] = function(state[1], key[round]) xor state[0]
            state[0] = temporary
        }
        state[0] = function(state[1], key[15]) xor state[0]
        inversePermutation(state, output)
    }

    private fun initialPermutation(input: ByteArray): UIntArray {
        val leftPositions = intArrayOf(57,49,41,33,25,17,9,1,59,51,43,35,27,19,11,3,61,53,45,37,29,21,13,5,63,55,47,39,31,23,15,7)
        val rightPositions = intArrayOf(56,48,40,32,24,16,8,0,58,50,42,34,26,18,10,2,60,52,44,36,28,20,12,4,62,54,46,38,30,22,14,6)
        var left = 0u
        var right = 0u
        repeat(32) { index ->
            left = left or bitNum(input, leftPositions[index], 31 - index)
            right = right or bitNum(input, rightPositions[index], 31 - index)
        }
        return uintArrayOf(left, right)
    }

    private fun inversePermutation(state: UIntArray, output: ByteArray) {
        val byteOrder = intArrayOf(3, 2, 1, 0, 7, 6, 5, 4)
        repeat(8) { group ->
            val base = 7 - group
            var value = 0
            repeat(4) { pair ->
                value = value or bitNumIntr(state[1], base + pair * 8, 7 - pair * 2).toInt()
                value = value or bitNumIntr(state[0], base + pair * 8, 6 - pair * 2).toInt()
            }
            output[byteOrder[group]] = value.toByte()
        }
    }

    private fun function(input: UInt, key: ByteArray): UInt {
        val t1 = bitNumIntL(input,31,0) or ((input and 0xf0000000u) shr 1) or bitNumIntL(input,4,5) or
            bitNumIntL(input,3,6) or ((input and 0x0f000000u) shr 3) or bitNumIntL(input,8,11) or
            bitNumIntL(input,7,12) or ((input and 0x00f00000u) shr 5) or bitNumIntL(input,12,17) or
            bitNumIntL(input,11,18) or ((input and 0x000f0000u) shr 7) or bitNumIntL(input,16,23)
        val t2 = bitNumIntL(input,15,0) or ((input and 0x0000f000u) shl 15) or bitNumIntL(input,20,5) or
            bitNumIntL(input,19,6) or ((input and 0x00000f00u) shl 13) or bitNumIntL(input,24,11) or
            bitNumIntL(input,23,12) or ((input and 0x000000f0u) shl 11) or bitNumIntL(input,28,17) or
            bitNumIntL(input,27,18) or ((input and 0x0000000fu) shl 9) or bitNumIntL(input,0,23)
        val large = byteArrayOf(
            (t1 shr 24).toByte(), (t1 shr 16).toByte(), (t1 shr 8).toByte(),
            (t2 shr 24).toByte(), (t2 shr 16).toByte(), (t2 shr 8).toByte(),
        )
        repeat(6) { large[it] = (large[it].toInt() xor key[it].toInt()).toByte() }
        fun value(box: Int, sixBits: Int) = SBOXES[box][sboxBit(sixBits)]
        var state = (value(0, (large[0].toInt() and 0xff) ushr 2).toUInt() shl 28) or
            (value(1, ((large[0].toInt() and 3) shl 4) or ((large[1].toInt() and 0xff) ushr 4)).toUInt() shl 24) or
            (value(2, ((large[1].toInt() and 15) shl 2) or ((large[2].toInt() and 0xff) ushr 6)).toUInt() shl 20) or
            (value(3, large[2].toInt() and 63).toUInt() shl 16) or
            (value(4, (large[3].toInt() and 0xff) ushr 2).toUInt() shl 12) or
            (value(5, ((large[3].toInt() and 3) shl 4) or ((large[4].toInt() and 0xff) ushr 4)).toUInt() shl 8) or
            (value(6, ((large[4].toInt() and 15) shl 2) or ((large[5].toInt() and 0xff) ushr 6)).toUInt() shl 4) or
            value(7, large[5].toInt() and 63).toUInt()
        val positions = intArrayOf(15,6,19,20,28,11,27,16,0,14,22,25,4,17,30,9,1,7,23,13,31,26,2,8,18,12,29,5,21,10,3,24)
        var permuted = 0u
        positions.forEachIndexed { index, position -> permuted = permuted or bitNumIntL(state, position, index) }
        return permuted
    }

    private fun bitNum(bytes: ByteArray, bit: Int, shift: Int): UInt {
        val index = bit / 32 * 4 + 3 - bit % 32 / 8
        return ((((bytes[index].toInt() and 0xff) ushr (7 - bit % 8)) and 1).toUInt() shl shift)
    }

    private fun bitNumIntr(value: UInt, bit: Int, shift: Int): Byte =
        (((value shr (31 - bit)) and 1u) shl shift).toByte()

    private fun bitNumIntL(value: UInt, bit: Int, shift: Int): UInt =
        ((value shl bit) and 0x80000000u) shr shift

    private fun sboxBit(value: Int): Int = (value and 0x20) or ((value and 0x1f) ushr 1) or ((value and 1) shl 4)

    private val SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val SBOXES = arrayOf(
        intArrayOf(14,4,13,1,2,15,11,8,3,10,6,12,5,9,0,7,0,15,7,4,14,2,13,1,10,6,12,11,9,5,3,8,4,1,14,8,13,6,2,11,15,12,9,7,3,10,5,0,15,12,8,2,4,9,1,7,5,11,3,14,10,0,6,13),
        intArrayOf(15,1,8,14,6,11,3,4,9,7,2,13,12,0,5,10,3,13,4,7,15,2,8,15,12,0,1,10,6,9,11,5,0,14,7,11,10,4,13,1,5,8,12,6,9,3,2,15,13,8,10,1,3,15,4,2,11,6,7,12,0,5,14,9),
        intArrayOf(10,0,9,14,6,3,15,5,1,13,12,7,11,4,2,8,13,7,0,9,3,4,6,10,2,8,5,14,12,11,15,1,13,6,4,9,8,15,3,0,11,1,2,12,5,10,14,7,1,10,13,0,6,9,8,7,4,15,14,3,11,5,2,12),
        intArrayOf(7,13,14,3,0,6,9,10,1,2,8,5,11,12,4,15,13,8,11,5,6,15,0,3,4,7,2,12,1,10,14,9,10,6,9,0,12,11,7,13,15,1,3,14,5,2,8,4,3,15,0,6,10,10,13,8,9,4,5,11,12,7,2,14),
        intArrayOf(2,12,4,1,7,10,11,6,8,5,3,15,13,0,14,9,14,11,2,12,4,7,13,1,5,0,15,10,3,9,8,6,4,2,1,11,10,13,7,8,15,9,12,5,6,3,0,14,11,8,12,7,1,14,2,13,6,15,0,9,10,4,5,3),
        intArrayOf(12,1,10,15,9,2,6,8,0,13,3,4,14,7,5,11,10,15,4,2,7,12,9,5,6,1,13,14,0,11,3,8,9,14,15,5,2,8,12,3,7,0,4,10,1,13,11,6,4,3,2,12,9,5,15,10,11,14,1,7,6,0,8,13),
        intArrayOf(4,11,2,14,15,0,8,13,3,12,9,7,5,10,6,1,13,0,11,7,4,9,1,10,14,3,5,12,2,15,8,6,1,4,11,13,12,3,7,14,10,15,6,8,0,5,9,2,6,11,13,8,1,4,10,7,9,5,0,15,14,2,3,12),
        intArrayOf(13,2,8,4,6,15,11,1,10,9,3,14,5,0,12,7,1,15,13,8,10,3,7,4,12,5,6,11,0,14,9,2,7,11,4,1,9,12,14,2,0,6,10,13,15,3,5,8,2,1,14,7,4,10,8,13,15,12,9,0,3,5,6,11),
    )
}
