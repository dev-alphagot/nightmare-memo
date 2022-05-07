/*
 * Copyright (c) 2022 AlphaGot
 *
 *  Licensed under the General Public License, Version 3.0. (https://opensource.org/licenses/gpl-3.0/)
 */

package com.alphagot.nightmarememo

import com.alphagot.nightmarememo.NMConfig.prefix
import io.github.monun.kommand.Kommand.Companion.register
import io.github.monun.kommand.StringType
import io.github.monun.kommand.getValue
import io.github.monun.kommand.kommand
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/***
 * @author AlphaGot
 */

class Main : JavaPlugin(), Listener {

    companion object {
        lateinit var instance: Main
            private set
    }

    enum class InputState {
        NONE,
        INPUTTING
    }

    private val isInputting: MutableMap<UUID, InputState> = mutableMapOf()
    private val configFile = File(dataFolder, "config.yml")

    private lateinit var memos: YamlConfiguration

    private val memoTmpBuffer: MutableMap<UUID, MutableList<String>> = mutableMapOf()
    private val memoTmpKeyBuffer: MutableMap<UUID, String> = mutableMapOf()
    private val memoTmpTitleBuffer: MutableMap<UUID, String> = mutableMapOf()

    private fun send(p: Player, t: Component){
        p.sendMessage(prefix.append(t))
    }

    private fun String.encryptECB(key: String): String {
        val kb: ByteArray = if(key.toByteArray().size < 32){
            key.toByteArray() + ByteArray(32 - key.toByteArray().size)
        } else key.toByteArray().toList().subList(0, 32).toByteArray()

        val keySpec = SecretKeySpec(kb, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val ciphertext = cipher.doFinal(this.toByteArray())
        val encodedByte = Base64.getEncoder().encode(ciphertext)
        return String(encodedByte)
    }

    private fun String.decryptECB(key: String): String {
        val kb: ByteArray = if(key.toByteArray().size < 32){
            key.toByteArray() + ByteArray(32 - key.toByteArray().size)
        } else key.toByteArray().toList().subList(0, 32).toByteArray()

        val keySpec = SecretKeySpec(kb, "AES")
        val decodedByte: ByteArray = Base64.getDecoder().decode(this)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val output = cipher.doFinal(decodedByte)
        return String(output)
    }

    override fun onEnable() {
        instance = this

        logger.info("이 바이트코드들은 모두 가상머신용이며, 여기에 등장하는 문자열, 변수, 상수 등은 전부 VM들이했습니다.")

        val ff = File(dataFolder, "memos.yml")
        if(!ff.canRead() && !ff.exists()) ff.createNewFile()

        memos = YamlConfiguration.loadConfiguration(
            ff
        )

        dataFolder.mkdir()

        File(dataFolder, "memos").mkdir()
        server.offlinePlayers.forEach {
            File(dataFolder, "memos/${it.uniqueId}").mkdir()
        }

        server.pluginManager.registerEvents(this, this)

        kommand {
            register("memo"){
                executes {
                    player.performCommand("/memo help")
                }
                then("help") {
                    executes {
                        send(player, text("메모 도움말"))
                        send(player, text("/memo help: 이 메시지를 보여줍니다."))
                        send(player, text("/memo create (암호) <메모 제목>: 새 메모를 만듭니다. 메모 제목은 띄어쓰기 사용이 가능합니다. 암호가 필요 없다면 암호 자리에 'null'을 넣어주세요."))
                        send(player, text("/memo list: 지금까지 쓴 메모들을 보여줍니다."))
                        send(player, text("/memo read (암호) <메모 제목>: 메모를 읽습니다. 암호가 걸려있지 않다면 암호 자리에 'null'을 넣어주세요."))
                        send(player, text("/memo delete <메모 제목>: 메모를 지웁니다."))
                    }
                }
                then("create"){
                    executes {
                        send(player, text("설정할 암호와 메모 제목을 입력해주세요."))
                    }
                    then("password" to string()){
                        executes {
                            send(player, text("메모 제목을 입력해주세요."))
                        }
                        then("title" to string(StringType.GREEDY_PHRASE)){
                            executes {
                                val title: String by it
                                val password: String by it

                                var memoList = mutableListOf<MemoMetadata>()

                                if(memos.contains(player.uniqueId.toString())) memoList = memos.getList(player.uniqueId.toString()) as MutableList<MemoMetadata>

                                memoList.add(
                                    MemoMetadata(
                                        title,
                                        password != "null"
                                    )
                                )

                                memos[player.uniqueId.toString()] = memoList

                                memoTmpBuffer[player.uniqueId] = mutableListOf()
                                memoTmpKeyBuffer[player.uniqueId] = password
                                memoTmpTitleBuffer[player.uniqueId] = title
                                isInputting[player.uniqueId] = InputState.INPUTTING

                                send(player, text("메모의 내용을 입력해주세요! (저장하려면 '__끝내기__'를 입력해주세요)"))
                            }
                        }
                    }
                }
                then("list"){
                    executes {
                        if(!memos.contains(player.uniqueId.toString())){
                            send(player, text("메모가 하나도 없습니다."))
                            return@executes
                        }

                        send(player, text("메모가 ${memos.getList(player.uniqueId.toString())!!.size}개 있습니다."))
                        memos.getList(player.uniqueId.toString())!!.forEach {
                            it as MemoMetadata

                            send(player, text("${it.title}(이)라는 메모가 있으며 암호화되어있${if(it.isEncrypted) "" else "지 않"}습니다."))
                        }
                    }
                }
                then("read"){
                    executes {
                        send(player, text("메모에 걸린 암호와 메모 제목을 입력해주세요."))
                    }
                    then("password" to string()){
                        executes {
                            send(player, text("볼 메모 제목을 입력해주세요."))
                        }
                        then("title" to string(StringType.GREEDY_PHRASE)){
                            executes {
                                val title: String by it
                                val password: String by it

                                var memoList = mutableListOf<MemoMetadata>()

                                if(memos.contains(player.uniqueId.toString())) memoList = memos.getList(player.uniqueId.toString()) as MutableList<MemoMetadata>

                                if(memoList.none { memo -> memo.title == title }){
                                    send(player, text("${title}(이)라는 메모가 없습니다."))
                                    return@executes
                                }
                                else {
                                    val memoData = File(dataFolder, "memos/${player.uniqueId}/${title.hashCode()}.txt").readText().split("\n")

                                    memoData.forEach {
                                        send(player, text(it.decryptECB(password)))
                                    }
                                }
                            }
                        }
                    }
                }
                then("delete"){
                    executes {
                        send(player, text("지울 메모 제목을 입력해주세요."))
                    }
                    then("title" to string(StringType.GREEDY_PHRASE)){
                        executes {
                            val title: String by it

                            var memoList = mutableListOf<MemoMetadata>()

                            if(memos.contains(player.uniqueId.toString())) memoList = memos.getList(player.uniqueId.toString()) as MutableList<MemoMetadata>

                            if(memoList.none { memo -> memo.title == title }){
                                send(player, text("${title}(이)라는 메모가 없습니다."))
                                return@executes
                            }
                            else {
                                memoList.removeIf { it.title == title }

                                memos[player.uniqueId.toString()] = memoList
                                File(dataFolder, "memos/${player.uniqueId}/${title.hashCode()}.txt").delete()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDisable() {
        logger.info("i3 개같은 거")

        memos.save(File(dataFolder, "memos.yml"))
    }

    @EventHandler
    fun onChat(e: AsyncChatEvent){
        if(isInputting[e.player.uniqueId] == InputState.INPUTTING){
            e.isCancelled = true

            if(PlainTextComponentSerializer.plainText().serialize(e.message()) != "__끝내기__"){
                val st = if(memoTmpKeyBuffer[e.player.uniqueId] != "null")
                    PlainTextComponentSerializer.plainText().serialize(e.message()).encryptECB(memoTmpKeyBuffer[e.player.uniqueId]!!)
                else
                    PlainTextComponentSerializer.plainText().serialize(e.message())
                memoTmpBuffer[e.player.uniqueId]!!.add(st)

                send(e.player, text("${memoTmpBuffer[e.player.uniqueId]!!.size}번째 줄: ${PlainTextComponentSerializer.plainText().serialize(e.message())}"))
            }
            else {
                isInputting[e.player.uniqueId] = InputState.NONE
                File(dataFolder, "memos/${e.player.uniqueId}/${memoTmpTitleBuffer[e.player.uniqueId]!!.hashCode()}.txt").writeText(memoTmpBuffer[e.player.uniqueId]!!.joinToString("\n"))

                memoTmpBuffer.remove(e.player.uniqueId)
                memoTmpKeyBuffer.remove(e.player.uniqueId)
                memoTmpTitleBuffer.remove(e.player.uniqueId)

                send(e.player, text("메모를 저장했습니다."))
            }
        }
    }
}