import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

import 'resume_button.dart';

class VideoPlayerComponent extends StatefulWidget {
  const VideoPlayerComponent({Key? key, required this.path}) : super(key: key);

  final String path;

  @override
  State<VideoPlayerComponent> createState() => _VideoPlayerComponentState();
}

class _VideoPlayerComponentState extends State<VideoPlayerComponent> {
  late final VideoPlayerController _controller;

  @override
  void initState() {
    super.initState();

    _controller = VideoPlayerController.asset(
        // 'assets/video.mp4'
        widget.path)
      ..initialize().then((_) {
        setState(() {});
      });
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Center(
        child: SizedBox(
          width: 250,
          // height: 200,
          child: AspectRatio(
            aspectRatio: _controller.value.aspectRatio,
            child:
            Stack(
              children: [
                VideoPlayer(_controller),
                Center(
                    child: ResumeButton(
                  controller: _controller,
                  iconSize: 44,
                )),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
