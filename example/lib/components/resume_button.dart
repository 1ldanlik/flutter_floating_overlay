import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

class ResumeButton extends StatefulWidget {
  const ResumeButton({
    Key? key,
    required this.controller,
    this.iconSize = 24,
    this.color = Colors.white,
  }) : super(key: key);

  final Color color;
  final double iconSize;
  final VideoPlayerController controller;

  @override
  State<ResumeButton> createState() => _ResumeButtonState();
}

class _ResumeButtonState extends State<ResumeButton> {
  late final VideoPlayerController controller;

  @override
  void initState() {
    super.initState();
    controller = widget.controller;
  }

  @override
  Widget build(BuildContext context) {
    return IconButton(
      iconSize: widget.iconSize,
        color: widget.color,
        onPressed: () {
          setState(() {
            controller.value.isPlaying ? controller.pause() : controller.play();
          });
        },
        icon:
            Icon(controller.value.isPlaying ? Icons.pause : Icons.play_arrow));
  }
}
